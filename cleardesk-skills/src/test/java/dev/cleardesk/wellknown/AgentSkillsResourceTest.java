package dev.cleardesk.wellknown;

import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;

@QuarkusTest
class AgentSkillsResourceTest {

    @Test
    void wellKnownListsThreeSkillsFromClasspath() {
        given().when()
                .get("/.well-known/agent-skills")
                .then()
                .statusCode(200)
                .body("version", is("cleardesk-v1"))
                .body("skills.size()", is(3))
                .body("skills.name", org.hamcrest.Matchers.hasItems("support-triage", "finance-ops", "dev-ops"));
    }
}
