package dev.mainthread.bobweb.session;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dev.mainthread.bobweb.acp.AcpConnection;
import dev.mainthread.bobweb.acp.AcpEvent;
import dev.mainthread.bobweb.api.ApiException;
import dev.mainthread.bobweb.api.ApiModels.CommandView;
import dev.mainthread.bobweb.api.ApiModels.ConversationSummary;
import dev.mainthread.bobweb.api.ApiModels.ConversationView;
import dev.mainthread.bobweb.api.ApiModels.ModeView;
import dev.mainthread.bobweb.api.ApiModels.UiEvent;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.AvailableCommand;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.AvailableCommandsUpdate;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ContentChunk;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.CurrentModeUpdate;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.InitializeResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.NewSessionResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.PermissionOption;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.PermissionOptionKind;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.Plan;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.RequestPermissionRequest;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.RequestPermissionResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.SelectedPermissionOutcome;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.SessionInfoUpdate;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.SessionMode;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.TextContent;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ToolCall;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ToolCallUpdate;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.UsageUpdate;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;

public final class Conversation implements AutoCloseable {

    private static final int EVENT_HISTORY_LIMIT = 2_000;

    private final String id;
    private final Path workspace;
    private final Duration permissionTimeout;
    private final AtomicLong eventSequence = new AtomicLong();
    private final List<UiEvent> events = new ArrayList<>();
    private final List<MultiEmitter<? super UiEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final Map<String, PendingPermission> pendingPermissions = new LinkedHashMap<>();

    private volatile AcpConnection connection;
    private volatile String agentSessionId;
    private volatile String title = "New conversation";
    private volatile String status = "connecting";
    private volatile String currentMode;
    private volatile Instant updatedAt = Instant.now();
    private volatile String agentName = "Bob";
    private volatile String agentVersion = "unknown";
    private volatile List<ModeView> modes = List.of();
    private volatile List<CommandView> commands = List.of();

    public Conversation(String id, Path workspace, Duration permissionTimeout) {
        this.id = id;
        this.workspace = workspace;
        this.permissionTimeout = permissionTimeout;
    }

    public void attach(AcpConnection connection, InitializeResponse initialized, NewSessionResponse session) {
        this.connection = connection;
        this.agentSessionId = session.sessionId();
        if (initialized.agentInfo() != null) {
            this.agentName = initialized.agentInfo().title() == null ? initialized.agentInfo().name()
                    : initialized.agentInfo().title();
            this.agentVersion = initialized.agentInfo().version();
        }
        if (session.modes() != null) {
            this.modes = session.modes().availableModes().stream().map(Conversation::toModeView).toList();
            this.currentMode = session.modes().currentModeId();
        }
        this.status = "ready";
        emit("session_ready", Map.of(
                "agent", agentName,
                "version", valueOr(agentVersion, "unknown"),
                "protocolVersion", valueOr(initialized.protocolVersion(), 1)));
    }

    public synchronized void beginTurn(String prompt) {
        if (!"ready".equals(status)) {
            throw new ApiException(409, "This conversation is not ready for another prompt");
        }
        status = "running";
        if ("New conversation".equals(title)) {
            title = abbreviate(prompt.replaceAll("\\s+", " ").trim(), 56);
        }
        emit("user_message", Map.of("text", prompt));
    }

    public void completeTurn(String stopReason) {
        status = "ready";
        emit("turn_complete", Map.of("stopReason", stopReason));
    }

    public void failTurn(Throwable failure) {
        status = "ready";
        emit("error", Map.of("message", rootMessage(failure)));
    }

    public void markConnectionFailed(Throwable failure) {
        status = "error";
        emit("error", Map.of("message", rootMessage(failure)));
    }

    public void modeChanged(String modeId) {
        currentMode = modeId;
        emit("mode", Map.of("currentMode", modeId));
    }

    public void cancelRequested() {
        emit("cancel_requested", Map.of());
    }

    public void onAcpEvent(AcpEvent event) {
        if (agentSessionId != null && !agentSessionId.equals(event.sessionId())) {
            return;
        }

        switch (event.type()) {
            case "agent_message_chunk" -> emitContent("agent_message_chunk", (ContentChunk) event.update());
            case "agent_thought_chunk" -> emitContent("thought_chunk", (ContentChunk) event.update());
            case "user_message_chunk" -> emitContent("agent_user_echo", (ContentChunk) event.update());
            case "tool_call" -> emitTool("tool_call", (ToolCall) event.update());
            case "tool_call_update" -> emitToolUpdate((ToolCallUpdate) event.update());
            case "plan" -> emit("plan", Map.of("entries", ((Plan) event.update()).entries()));
            case "available_commands_update" -> updateCommands((AvailableCommandsUpdate) event.update());
            case "current_mode_update" -> modeChanged(((CurrentModeUpdate) event.update()).currentModeId());
            case "session_info_update" -> updateSessionInfo((SessionInfoUpdate) event.update());
            case "usage_update" -> emitUsage((UsageUpdate) event.update());
            case "config_option_update" -> emit("config_options", Map.of("options", event.update()));
            default -> emit("protocol_event", Map.of("name", event.type(), "payload", event.update()));
        }
    }

    public CompletionStage<RequestPermissionResponse> requestPermission(RequestPermissionRequest request) {
        ToolCallUpdate toolCall = request.toolCall();
        String toolCallId = toolCall.toolCallId();
        List<PermissionOption> options = request.options() == null ? List.of() : List.copyOf(request.options());
        CompletableFuture<RequestPermissionResponse> decision = new CompletableFuture<>();
        PendingPermission pending = new PendingPermission(options, decision);

        synchronized (pendingPermissions) {
            pendingPermissions.put(toolCallId, pending);
        }
        emit("permission_requested", Map.of(
                "toolCallId", toolCallId,
                "title", valueOr(toolCall.title(), "Bob wants to use a tool"),
                "kind", enumValue(toolCall.kind()),
                "rawInput", valueOr(toolCall.rawInput(), Map.of()),
                "content", valueOr(toolCall.content(), List.of()),
                "options", options.stream().map(Conversation::permissionOption).toList()));

        CompletableFuture.delayedExecutor(permissionTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(pending::completeOnTimeout);
        return decision.whenComplete((response, failure) -> {
            synchronized (pendingPermissions) {
                pendingPermissions.remove(toolCallId);
            }
            if (failure == null) {
                emit("permission_decided", Map.of(
                        "toolCallId", toolCallId,
                        "optionId", selectedOption(response),
                        "timedOut", pending.timedOut()));
            }
        });
    }

    public void decidePermission(String toolCallId, String optionId) {
        PendingPermission pending;
        synchronized (pendingPermissions) {
            pending = pendingPermissions.get(toolCallId);
        }
        if (pending == null) {
            throw new ApiException(404, "This permission request is no longer pending");
        }
        boolean valid = pending.options().stream().anyMatch(option -> option.optionId().equals(optionId));
        if (!valid) {
            throw new ApiException(400, "Unknown permission option");
        }
        pending.complete(optionId);
    }

    public boolean supportsMode(String modeId) {
        return modes.stream().anyMatch(mode -> mode.id().equals(modeId));
    }

    public AcpConnection connection() {
        AcpConnection current = connection;
        if (current == null) {
            throw new ApiException(503, "Bob is not connected yet");
        }
        return current;
    }

    public String agentSessionId() {
        return agentSessionId;
    }

    public Multi<UiEvent> eventStream(long afterSequence) {
        return Multi.createFrom().emitter(emitter -> {
            synchronized (events) {
                events.stream().filter(event -> event.sequence() > afterSequence).forEach(emitter::emit);
                subscribers.add(emitter);
            }
            emitter.onTermination(() -> subscribers.remove(emitter));
        });
    }

    public ConversationSummary summary() {
        return new ConversationSummary(id, agentSessionId, title, workspace.toString(), status, currentMode, updatedAt);
    }

    public ConversationView view() {
        List<UiEvent> eventSnapshot;
        synchronized (events) {
            eventSnapshot = List.copyOf(events);
        }
        return new ConversationView(id, agentSessionId, title, workspace.toString(), status, currentMode, updatedAt,
                agentName, agentVersion, modes, commands, eventSnapshot);
    }

    @Override
    public void close() {
        status = "closed";
        AcpConnection current = connection;
        if (current != null) {
            current.close();
        }
        subscribers.forEach(MultiEmitter::complete);
        subscribers.clear();
    }

    private void emitContent(String type, ContentChunk chunk) {
        Object content = chunk.content();
        if (content instanceof TextContent text) {
            emit(type, Map.of("text", text.text()));
        } else if (content instanceof Map<?, ?> map && map.get("text") instanceof String text) {
            emit(type, Map.of("text", text));
        } else {
            emit(type, Map.of("content", content));
        }
    }

    private void emitTool(String type, ToolCall toolCall) {
        emit(type, toolData(toolCall.toolCallId(), toolCall.title(), toolCall.kind(), toolCall.status(),
                toolCall.rawInput(), toolCall.rawOutput(), toolCall.content()));
    }

    private void emitToolUpdate(ToolCallUpdate toolCall) {
        emit("tool_update", toolData(toolCall.toolCallId(), toolCall.title(), toolCall.kind(), toolCall.status(),
                toolCall.rawInput(), toolCall.rawOutput(), toolCall.content()));
    }

    private Map<String, Object> toolData(String id, String toolTitle, Object kind, Object toolStatus, Object input,
            Object output, Object content) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolCallId", id);
        data.put("title", valueOr(toolTitle, "Tool call"));
        data.put("kind", enumValue(kind));
        data.put("status", enumValue(toolStatus));
        data.put("rawInput", valueOr(input, Map.of()));
        data.put("rawOutput", valueOr(output, Map.of()));
        data.put("content", valueOr(content, List.of()));
        return data;
    }

    private void updateCommands(AvailableCommandsUpdate update) {
        List<AvailableCommand> available = update.availableCommands() == null ? List.of() : update.availableCommands();
        commands = available.stream()
                .map(command -> new CommandView(command.name(), command.description(), command.input()))
                .sorted(Comparator.comparing(CommandView::name))
                .toList();
        emit("commands", Map.of("commands", commands));
    }

    private void updateSessionInfo(SessionInfoUpdate update) {
        if (update.title() != null && !update.title().isBlank()) {
            title = update.title();
        }
        emit("session_info", Map.of("title", title, "updatedAt", valueOr(update.updatedAt(), "")));
    }

    private void emitUsage(UsageUpdate usage) {
        emit("usage", Map.of(
                "used", valueOr(usage.used(), 0),
                "size", valueOr(usage.size(), 0),
                "cost", valueOr(usage.cost(), Map.of())));
    }

    private void emit(String type, Map<String, Object> data) {
        UiEvent event = new UiEvent(eventSequence.incrementAndGet(), type, Instant.now(), data);
        synchronized (events) {
            events.add(event);
            if (events.size() > EVENT_HISTORY_LIMIT) {
                events.remove(0);
            }
            updatedAt = event.at();
            subscribers.removeIf(MultiEmitter::isCancelled);
            subscribers.forEach(subscriber -> subscriber.emit(event));
        }
    }

    private static ModeView toModeView(SessionMode mode) {
        return new ModeView(mode.id(), mode.name(), mode.description());
    }

    private static Map<String, Object> permissionOption(PermissionOption option) {
        return Map.of(
                "id", option.optionId(),
                "name", option.name(),
                "kind", option.kind().getValue());
    }

    private static RequestPermissionResponse rejectResponse(List<PermissionOption> options) {
        return options.stream()
                .filter(option -> option.kind() == PermissionOptionKind.REJECT_ONCE
                        || option.kind() == PermissionOptionKind.REJECT_ALWAYS)
                .findFirst()
                .map(option -> new RequestPermissionResponse(new SelectedPermissionOutcome(option.optionId())))
                .orElseGet(() -> new RequestPermissionResponse(Map.of("outcome", "cancelled")));
    }

    private static String selectedOption(RequestPermissionResponse response) {
        if (response.outcome() instanceof SelectedPermissionOutcome selected) {
            return selected.optionId();
        }
        return "cancelled";
    }

    private static String enumValue(Object value) {
        if (value == null) {
            return "unknown";
        }
        try {
            return Objects.toString(value.getClass().getMethod("getValue").invoke(value));
        } catch (ReflectiveOperationException ignored) {
            return value.toString().toLowerCase();
        }
    }

    private static <T> T valueOr(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static String abbreviate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength - 1) + "…";
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return valueOr(current.getMessage(), current.getClass().getSimpleName());
    }

    private record PendingPermission(List<PermissionOption> options,
            CompletableFuture<RequestPermissionResponse> decision, RequestPermissionResponse timeoutResponse,
            AtomicBoolean timeoutUsed) {

        PendingPermission(List<PermissionOption> options, CompletableFuture<RequestPermissionResponse> decision) {
            this(options, decision, rejectResponse(options), new AtomicBoolean());
        }

        void complete(String optionId) {
            decision.complete(new RequestPermissionResponse(new SelectedPermissionOutcome(optionId)));
        }

        synchronized void completeOnTimeout() {
            if (!decision.isDone()) {
                timeoutUsed.set(true);
                decision.complete(timeoutResponse);
            }
        }

        boolean timedOut() {
            return timeoutUsed.get();
        }
    }
}
