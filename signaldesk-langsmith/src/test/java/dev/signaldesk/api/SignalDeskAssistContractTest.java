package dev.signaldesk.api;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.signaldesk.testsupport.SignalDeskStubChatModel;
import dev.signaldesk.testsupport.SignalDeskStubProfile;
import dev.signaldesk.tools.RunbookTools;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(SignalDeskStubProfile.class)
class SignalDeskAssistContractTest {

    @Test
    void plainChatDoesNotInvokeTool() {
        AssistResponse body = assist(SignalDeskStubChatModel.PLAIN_PROMPT);

        assertEquals(Outcome.OK, body.outcome());
        assertFalse(body.usedTool());
        assertNull(body.toolName());
        assertTrue(body.answer().toLowerCase().contains("sev-2") || body.answer().toLowerCase().contains("30"));
    }

    @Test
    void runbookQuestionInvokesTool() {
        AssistResponse body = assist(SignalDeskStubChatModel.TOOL_PROMPT);

        assertEquals(Outcome.OK, body.outcome());
        assertTrue(body.usedTool());
        assertEquals(RunbookTools.TOOL_NAME, body.toolName());
        assertTrue(body.answer().toLowerCase().contains("runbook") || body.answer().toLowerCase().contains("escalate"));
    }

    @Test
    void unknownPlanReportsToolFailure() {
        AssistResponse body = assist(SignalDeskStubChatModel.FAILURE_PROMPT);

        assertEquals(Outcome.TOOL_FAILED, body.outcome());
        assertTrue(body.usedTool());
        assertEquals(RunbookTools.TOOL_NAME, body.toolName());
        assertTrue(body.answer().toLowerCase().contains("failed") || body.answer().toLowerCase().contains("not found"));
    }

    private static AssistResponse assist(String question) {
        return given().contentType("application/json")
                .body("{\"question\":\"" + escapeJson(question) + "\"}")
                .post("/signaldesk/assist")
                .then()
                .statusCode(200)
                .extract()
                .as(AssistResponse.class);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
