package dev.kbagent;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
class AgentCardResourceTest {

    @Test
    void wellKnownAgentCardIsServed() {
        RestAssured.given()
                .when()
                .get("/.well-known/agent-card.json")
                .then()
                .statusCode(200)
                .body("name", equalTo("DeskFlow Knowledge Base Agent"));
    }
}