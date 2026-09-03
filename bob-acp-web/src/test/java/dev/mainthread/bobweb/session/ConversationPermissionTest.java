package dev.mainthread.bobweb.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import dev.mainthread.bobweb.acp.AcpEvent;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ContentChunk;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.PermissionOption;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.PermissionOptionKind;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.RequestPermissionRequest;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.RequestPermissionResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.SelectedPermissionOutcome;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.ToolCallUpdate;

class ConversationPermissionTest {

    @Test
    void extractsTextFromTheUntypedAcpContentUnion() {
        Conversation conversation = new Conversation("web-1", Path.of("."), Duration.ofSeconds(1));

        conversation.onAcpEvent(new AcpEvent("agent-1", "agent_message_chunk",
                new ContentChunk(Map.of("type", "text", "text", "Hello from Bob"))));

        assertEquals("Hello from Bob", conversation.view().events().getFirst().data().get("text"));
    }

    @Test
    void completesWithTheUsersExactChoice() throws Exception {
        Conversation conversation = new Conversation("web-1", Path.of("."), Duration.ofSeconds(1));
        List<PermissionOption> options = options();

        var response = conversation.requestPermission(
                new RequestPermissionRequest(options, "agent-1", new ToolCallUpdate("tool-1")));
        conversation.decidePermission("tool-1", "allow-once");

        RequestPermissionResponse permission = response.toCompletableFuture().get(1, TimeUnit.SECONDS);
        SelectedPermissionOutcome outcome = (SelectedPermissionOutcome) permission.outcome();
        assertEquals("allow-once", outcome.optionId());
        assertTrue(conversation.view().events().stream()
                .anyMatch(event -> "permission_decided".equals(event.type())));
    }

    @Test
    void failsClosedWhenNobodyAnswers() throws Exception {
        Conversation conversation = new Conversation("web-1", Path.of("."), Duration.ofMillis(20));

        RequestPermissionResponse permission = conversation.requestPermission(
                new RequestPermissionRequest(options(), "agent-1", new ToolCallUpdate("tool-1")))
                .toCompletableFuture().get(1, TimeUnit.SECONDS);

        SelectedPermissionOutcome outcome = (SelectedPermissionOutcome) permission.outcome();
        assertEquals("reject-once", outcome.optionId());
        assertTrue(conversation.view().events().stream()
                .filter(event -> "permission_decided".equals(event.type()))
                .anyMatch(event -> Boolean.TRUE.equals(event.data().get("timedOut"))));
    }

    private static List<PermissionOption> options() {
        return List.of(
                new PermissionOption(PermissionOptionKind.ALLOW_ONCE, "Allow once", "allow-once"),
                new PermissionOption(PermissionOptionKind.REJECT_ONCE, "Reject", "reject-once"));
    }
}
