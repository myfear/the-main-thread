package dev.mainthread.bobweb.acp;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.smallrye.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.AvailableCommandsUpdate;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.CancelNotification;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ClientCapabilities;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ConfigOptionUpdate;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ContentChunk;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.CurrentModeUpdate;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.Implementation;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.InitializeRequest;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.InitializeResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ListSessionsRequest;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ListSessionsResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.LoadSessionRequest;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.LoadSessionResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.NewSessionRequest;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.NewSessionResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.Plan;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.PromptRequest;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.PromptResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.RequestPermissionRequest;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.RequestPermissionResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ResumeSessionRequest;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ResumeSessionResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.SessionInfoUpdate;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.SetSessionModeRequest;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.SetSessionModeResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.TextContent;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ToolCall;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ToolCallUpdate;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.UsageUpdate;

final class SmallRyeAcpConnection implements AcpConnection {

    private static final Logger LOG = Logger.getLogger(SmallRyeAcpConnection.class);
    private static final Map<String, Class<?>> UPDATE_TYPES = Map.ofEntries(
            Map.entry("agent_message_chunk", ContentChunk.class),
            Map.entry("agent_thought_chunk", ContentChunk.class),
            Map.entry("user_message_chunk", ContentChunk.class),
            Map.entry("tool_call", ToolCall.class),
            Map.entry("tool_call_update", ToolCallUpdate.class),
            Map.entry("plan", Plan.class),
            Map.entry("available_commands_update", AvailableCommandsUpdate.class),
            Map.entry("current_mode_update", CurrentModeUpdate.class),
            Map.entry("config_option_update", ConfigOptionUpdate.class),
            Map.entry("session_info_update", SessionInfoUpdate.class),
            Map.entry("usage_update", UsageUpdate.class));

    private final StdioAcpClientTransport transport;
    private final ObjectMapper mapper;
    private final Duration requestTimeout;
    private final Duration promptTimeout;
    private final Consumer<AcpEvent> eventConsumer;
    private final Function<RequestPermissionRequest, CompletionStage<RequestPermissionResponse>> permissionHandler;
    private final AtomicInteger requestIds = new AtomicInteger();
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutScheduler;

    SmallRyeAcpConnection(StdioAcpClientTransport transport, Duration requestTimeout, Duration promptTimeout,
            Consumer<AcpEvent> eventConsumer,
            Function<RequestPermissionRequest, CompletionStage<RequestPermissionResponse>> permissionHandler) {
        this.transport = transport;
        this.mapper = transport.getMapper();
        this.requestTimeout = requestTimeout;
        this.promptTimeout = promptTimeout;
        this.eventConsumer = eventConsumer;
        this.permissionHandler = permissionHandler;
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bob-web-acp-timeouts");
            thread.setDaemon(true);
            return thread;
        });
        transport.setInboundMessageHandler(this::handleIncoming);
        transport.connect();
    }

    @Override
    public CompletionStage<InitializeResponse> initialize() {
        InitializeRequest request = new InitializeRequest(
                null,
                new ClientCapabilities(null, null, false),
                new Implementation(null, "bob-acp-web", "Bob Web", "1.0.0"),
                1);
        return request("initialize", request, InitializeResponse.class, requestTimeout);
    }

    @Override
    public CompletionStage<NewSessionResponse> newSession(Path workspace) {
        return request("session/new", new NewSessionRequest(workspace.toString(), List.of()), NewSessionResponse.class,
                requestTimeout);
    }

    @Override
    public CompletionStage<ListSessionsResponse> listSessions(Path workspace) {
        ListSessionsRequest request = new ListSessionsRequest(null, null, workspace.toString());
        return request("session/list", request, ListSessionsResponse.class, requestTimeout);
    }

    @Override
    public CompletionStage<LoadSessionResponse> loadSession(Path workspace, String sessionId) {
        LoadSessionRequest request = new LoadSessionRequest(workspace.toString(), List.of(), sessionId);
        return request("session/load", request, LoadSessionResponse.class, requestTimeout);
    }

    @Override
    public CompletionStage<ResumeSessionResponse> resumeSession(Path workspace, String sessionId) {
        ResumeSessionRequest request = new ResumeSessionRequest(null, workspace.toString(), List.of(), sessionId);
        return request("session/resume", request, ResumeSessionResponse.class, requestTimeout);
    }

    @Override
    public CompletionStage<SetSessionModeResponse> setMode(String sessionId, String modeId) {
        return request("session/set_mode", new SetSessionModeRequest(modeId, sessionId), SetSessionModeResponse.class,
                requestTimeout);
    }

    @Override
    public CompletionStage<PromptResponse> prompt(String sessionId, String prompt) {
        PromptRequest request = new PromptRequest(List.of(new TextContent(prompt)), sessionId);
        return request("session/prompt", request, PromptResponse.class, promptTimeout);
    }

    @Override
    public void cancel(String sessionId) {
        sendNotification("session/cancel", new CancelNotification(sessionId));
    }

    @Override
    public void close() {
        timeoutScheduler.shutdownNow();
        IllegalStateException closed = new IllegalStateException("ACP connection closed");
        for (CompletableFuture<JsonNode> pending : pendingRequests.values()) {
            pending.completeExceptionally(closed);
        }
        pendingRequests.clear();
        transport.closeGracefully();
    }

    private <T> CompletionStage<T> request(String method, Object params, Class<T> responseType, Duration timeout) {
        int id = requestIds.incrementAndGet();
        CompletableFuture<JsonNode> response = new CompletableFuture<>();
        pendingRequests.put(id, response);

        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", mapper.valueToTree(params));
        transport.sendMessage(request);

        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
            CompletableFuture<JsonNode> pending = pendingRequests.remove(id);
            if (pending != null) {
                pending.completeExceptionally(new IllegalStateException("ACP request timed out: " + method));
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        return response.whenComplete((result, failure) -> timeoutTask.cancel(false))
                .thenApply(result -> mapper.convertValue(result, responseType));
    }

    private void sendNotification(String method, Object params) {
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", mapper.valueToTree(params));
        transport.sendMessage(notification);
    }

    private void handleIncoming(JsonNode message) {
        if (message.has("id") && message.has("method")) {
            handleAgentRequest(message);
        } else if (message.has("id")) {
            handleResponse(message);
        } else if ("session/update".equals(message.path("method").asText())) {
            handleSessionUpdate(message.path("params"));
        }
    }

    private void handleResponse(JsonNode message) {
        int id = message.path("id").asInt();
        CompletableFuture<JsonNode> pending = pendingRequests.remove(id);
        if (pending == null) {
            LOG.debugf("Ignoring response for unknown ACP request %d", id);
            return;
        }
        if (message.hasNonNull("error")) {
            JsonNode error = message.path("error");
            pending.completeExceptionally(new IllegalStateException(
                    "ACP error " + error.path("code").asInt() + ": " + error.path("message").asText()));
        } else {
            pending.complete(message.path("result"));
        }
    }

    private void handleSessionUpdate(JsonNode params) {
        JsonNode updateNode = params.path("update");
        String type = updateNode.path("sessionUpdate").asText("unknown");
        Class<?> updateClass = UPDATE_TYPES.get(type);
        Object update = updateClass == null ? mapper.convertValue(updateNode, Object.class)
                : mapper.convertValue(updateNode, updateClass);
        eventConsumer.accept(new AcpEvent(params.path("sessionId").asText(), type, update));
    }

    private void handleAgentRequest(JsonNode message) {
        JsonNode id = message.path("id");
        String method = message.path("method").asText();
        if (!"session/request_permission".equals(method)) {
            sendError(id, -32601, "Method not found: " + method);
            return;
        }

        RequestPermissionRequest request = mapper.convertValue(message.path("params"), RequestPermissionRequest.class);
        try {
            permissionHandler.apply(request).whenComplete((response, failure) -> {
                if (failure == null) {
                    sendResponse(id, response);
                } else {
                    sendError(id, -32603, "Permission decision failed");
                }
            });
        } catch (RuntimeException failure) {
            LOG.warn("Permission handler failed", failure);
            sendError(id, -32603, "Permission decision failed");
        }
    }

    private void sendResponse(JsonNode id, Object result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", mapper.valueToTree(result));
        transport.sendMessage(response);
    }

    private void sendError(JsonNode id, int code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        transport.sendMessage(response);
    }
}
