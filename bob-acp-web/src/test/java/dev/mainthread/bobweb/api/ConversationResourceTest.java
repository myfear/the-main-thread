package dev.mainthread.bobweb.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.mainthread.bobweb.acp.AcpConnection;
import dev.mainthread.bobweb.acp.AcpConnectionFactory;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.Implementation;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.InitializeResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.NewSessionResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.PromptResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.SessionMode;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.SessionModeState;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.SetSessionModeResponse;
import io.smallrye.agentclientprotocol.sdk.spec.schema.v1.StopReason;

@QuarkusTest
class ConversationResourceTest {

    @InjectMock
    AcpConnectionFactory connectionFactory;

    AcpConnection connection;

    @BeforeEach
    void configureAgent() {
        connection = mock(AcpConnection.class);
        when(connectionFactory.open(any(), any())).thenReturn(connection);
        when(connection.initialize()).thenReturn(CompletableFuture.completedFuture(initializeResponse()));
        when(connection.newSession(any())).thenReturn(CompletableFuture.completedFuture(newSessionResponse()));
        when(connection.prompt(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new PromptResponse(StopReason.END_TURN)));
        when(connection.setMode(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new SetSessionModeResponse(Map.of())));
    }

    @Test
    void drivesAConversationThroughTheHttpApi() {
        String id = given()
                .contentType(ContentType.JSON)
                .body(Map.of("workspace", "."))
                .when().post("/api/conversations")
                .then()
                .statusCode(201)
                .body("agentSessionId", equalTo("agent-session-1"))
                .body("agentName", equalTo("Bob"))
                .body("modes", hasSize(3))
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("prompt", "Review the retry policy"))
                .when().post("/api/conversations/{id}/messages", id)
                .then()
                .statusCode(202)
                .body("status", equalTo("accepted"));

        given()
                .when().get("/api/conversations/{id}", id)
                .then()
                .statusCode(200)
                .body("title", equalTo("Review the retry policy"))
                .body("status", equalTo("ready"))
                .body("events.type", hasItem("user_message"))
                .body("events.type", hasItem("turn_complete"));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("modeId", "plan"))
                .when().put("/api/conversations/{id}/mode", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("changed"));
        verify(connection).setMode("agent-session-1", "plan");

        given()
                .when().delete("/api/conversations/{id}", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("closed"));
        verify(connection).close();
    }

    @Test
    void rejectsWorkspacesOutsideTheConfiguredRoot() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("workspace", "../outside"))
                .when().post("/api/conversations")
                .then()
                .statusCode(400)
                .body("message", equalTo("Workspace must stay inside the configured root"));
    }

    @Test
    void servesTheWebClient() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .contentType(ContentType.HTML)
                .body(org.hamcrest.Matchers.containsString("Bob Web"))
                .body(org.hamcrest.Matchers.containsString("src=\"/Bob.svg\""));

        given()
                .when().get("/Bob.svg")
                .then()
                .statusCode(200)
                .contentType("image/svg+xml");

        given()
                .when().get("/styles.css")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.containsString("--accent: #8a3ffc;"));
    }

    private static InitializeResponse initializeResponse() {
        return new InitializeResponse(Map.of(), null, new Implementation(Map.of(), "bob", "Bob", "2.0.2"), List.of(), 1);
    }

    private static NewSessionResponse newSessionResponse() {
        List<SessionMode> modes = List.of(
                new SessionMode("agent", "Agent"),
                new SessionMode("plan", "Plan"),
                new SessionMode("ask", "Ask"));
        return new NewSessionResponse(Map.of(), List.of(), new SessionModeState(modes, "agent"), "agent-session-1");
    }
}
