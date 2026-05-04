package dev.cleardesk.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.cleardesk.testsupport.ClearDeskStubChatModel;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;

@QuarkusTest
class SupervisorRoutingTest {

    @Test
    void routesSupportWhenPromptIsClearlySupport() {
        ClearDeskChatResponse body = given().contentType("application/json")
                .body("{\"prompt\":\"customer cannot authenticate; open SEV-2 ticket\",\"skillsEnabled\":true}")
                .post("/clear-desk/chat")
                .then()
                .statusCode(200)
                .extract()
                .as(ClearDeskChatResponse.class);

        assertEquals("SUPPORT", body.routedSpecialist);
    }

    @Test
    void routesFinanceWhenPromptIsClearlyFinance() {
        ClearDeskChatResponse body = given().contentType("application/json")
                .body("{\"prompt\":\"invoice refund for ticket INC-9\",\"skillsEnabled\":true}")
                .post("/clear-desk/chat")
                .then()
                .statusCode(200)
                .extract()
                .as(ClearDeskChatResponse.class);

        assertEquals("FINANCE", body.routedSpecialist);
    }

    @Test
    void routesDevOpsWhenPromptIsClearlyPlatform() {
        ClearDeskChatResponse body = given().contentType("application/json")
                .body("{\"prompt\":\"pipeline release-42 keeps failing on the build agent\",\"skillsEnabled\":true}")
                .post("/clear-desk/chat")
                .then()
                .statusCode(200)
                .extract()
                .as(ClearDeskChatResponse.class);

        assertEquals("DEVOPS", body.routedSpecialist);
    }

    @Test
    void ambiguousPromptRoutesToDevOpsWithoutSkillsBaseline() {
        String json = """
                {"prompt":"%s","skillsEnabled":false}
                """
                .formatted(ClearDeskStubChatModel.AMBIGUOUS_PROMPT.replace("\\", "\\\\").replace("\"", "\\\""));

        ClearDeskChatResponse body = given().contentType("application/json")
                .body(json)
                .post("/clear-desk/chat")
                .then()
                .statusCode(200)
                .extract()
                .as(ClearDeskChatResponse.class);

        assertEquals("DEVOPS", body.routedSpecialist);
    }

    @Test
    void ambiguousPromptRoutesToFinanceWithSkillsEnabled() {
        String json = """
                {"prompt":"%s","skillsEnabled":true}
                """
                .formatted(ClearDeskStubChatModel.AMBIGUOUS_PROMPT.replace("\\", "\\\\").replace("\"", "\\\""));

        ClearDeskChatResponse body = given().contentType("application/json")
                .body(json)
                .post("/clear-desk/chat")
                .then()
                .statusCode(200)
                .extract()
                .as(ClearDeskChatResponse.class);

        assertEquals("FINANCE", body.routedSpecialist);
    }
}
