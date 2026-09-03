package dev.mainthread.bobweb.session;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import dev.mainthread.bobweb.acp.AcpConnection;
import dev.mainthread.bobweb.acp.AcpConnectionFactory;
import dev.mainthread.bobweb.api.ApiException;
import dev.mainthread.bobweb.api.ApiModels.ActionAccepted;
import dev.mainthread.bobweb.api.ApiModels.ConversationSummary;
import dev.mainthread.bobweb.api.ApiModels.ConversationView;
import dev.mainthread.bobweb.api.ApiModels.UiEvent;
import dev.mainthread.bobweb.config.BobConfig;
import io.smallrye.mutiny.Multi;

@ApplicationScoped
public class ConversationService {

    private final AcpConnectionFactory connectionFactory;
    private final BobConfig config;
    private final Path workspaceRoot;
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    public ConversationService(AcpConnectionFactory connectionFactory, BobConfig config) {
        this.connectionFactory = connectionFactory;
        this.config = config;
        this.workspaceRoot = Path.of(config.workspaceRoot()).toAbsolutePath().normalize();
    }

    public List<ConversationSummary> list() {
        return conversations.values().stream()
                .map(Conversation::summary)
                .sorted(Comparator.comparing(ConversationSummary::updatedAt).reversed())
                .toList();
    }

    public ConversationView get(String id) {
        return conversation(id).view();
    }

    public CompletionStage<ConversationView> create(String requestedWorkspace) {
        Path workspace = resolveWorkspace(requestedWorkspace);
        Conversation conversation = new Conversation(UUID.randomUUID().toString(), workspace, config.permissionTimeout());
        reserve(conversation);

        CompletableFuture<ConversationView> result = new CompletableFuture<>();
        try {
            AcpConnection connection = connectionFactory.open(conversation::onAcpEvent, conversation::requestPermission);
            connection.initialize()
                    .thenCompose(initialized -> connection.newSession(workspace)
                            .thenApply(session -> {
                                conversation.attach(connection, initialized, session);
                                return conversation.view();
                            }))
                    .whenComplete((view, failure) -> {
                        if (failure == null) {
                            result.complete(view);
                        } else {
                            failCreation(conversation, failure, result);
                        }
                    });
        } catch (RuntimeException failure) {
            failCreation(conversation, failure, result);
        }
        return result;
    }

    public ActionAccepted sendMessage(String id, String prompt) {
        String cleanPrompt = requireText(prompt, "Prompt");
        Conversation conversation = conversation(id);
        conversation.beginTurn(cleanPrompt);
        try {
            conversation.connection().prompt(conversation.agentSessionId(), cleanPrompt)
                    .whenComplete((response, failure) -> {
                        if (failure == null) {
                            conversation.completeTurn(response.stopReason().getValue());
                        } else {
                            conversation.failTurn(failure);
                        }
                    });
        } catch (RuntimeException failure) {
            conversation.failTurn(failure);
            throw failure;
        }
        return new ActionAccepted("accepted");
    }

    public CompletionStage<ActionAccepted> changeMode(String id, String modeId) {
        String cleanMode = requireText(modeId, "Mode");
        Conversation conversation = conversation(id);
        if (!conversation.supportsMode(cleanMode)) {
            throw new ApiException(400, "Bob did not advertise that mode");
        }
        return conversation.connection().setMode(conversation.agentSessionId(), cleanMode)
                .thenApply(ignored -> {
                    conversation.modeChanged(cleanMode);
                    return new ActionAccepted("changed");
                });
    }

    public ActionAccepted decidePermission(String id, String toolCallId, String optionId) {
        conversation(id).decidePermission(toolCallId, requireText(optionId, "Permission option"));
        return new ActionAccepted("decided");
    }

    public ActionAccepted cancel(String id) {
        Conversation conversation = conversation(id);
        conversation.connection().cancel(conversation.agentSessionId());
        conversation.cancelRequested();
        return new ActionAccepted("cancel-requested");
    }

    public ActionAccepted close(String id) {
        Conversation conversation = conversations.remove(id);
        if (conversation == null) {
            throw new ApiException(404, "Conversation not found");
        }
        conversation.close();
        return new ActionAccepted("closed");
    }

    public Multi<UiEvent> events(String id, long afterSequence) {
        if (afterSequence < 0) {
            throw new ApiException(400, "Event sequence must not be negative");
        }
        return conversation(id).eventStream(afterSequence);
    }

    @PreDestroy
    void stopAgents() {
        conversations.values().forEach(Conversation::close);
        conversations.clear();
    }

    private synchronized void reserve(Conversation conversation) {
        if (conversations.size() >= config.maxConversations()) {
            throw new ApiException(429, "Maximum number of Bob conversations reached");
        }
        conversations.put(conversation.summary().id(), conversation);
    }

    private Path resolveWorkspace(String requestedWorkspace) {
        String requested = requestedWorkspace == null || requestedWorkspace.isBlank() ? "." : requestedWorkspace.trim();
        Path relative = Path.of(requested);
        if (relative.isAbsolute()) {
            throw new ApiException(400, "Workspace must be relative to the configured root");
        }
        Path resolved = workspaceRoot.resolve(relative).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new ApiException(400, "Workspace must stay inside the configured root");
        }
        if (!Files.isDirectory(resolved)) {
            throw new ApiException(400, "Workspace directory does not exist: " + requested);
        }
        return resolved;
    }

    private Conversation conversation(String id) {
        Conversation conversation = conversations.get(id);
        if (conversation == null) {
            throw new ApiException(404, "Conversation not found");
        }
        return conversation;
    }

    private void failCreation(Conversation conversation, Throwable failure, CompletableFuture<ConversationView> result) {
        conversations.remove(conversation.summary().id());
        conversation.markConnectionFailed(failure);
        conversation.close();
        result.completeExceptionally(new ApiException(502, "Could not start Bob: " + rootMessage(failure)));
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, label + " must not be blank");
        }
        return value.trim();
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current.getCause() != null) && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
