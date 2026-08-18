package com.ibm.developer.shieldstral;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(ModelStubResource.class)
public class PolicyGateResourceTest {

    private static final String LAB_REQUEST = "Write a credential-stuffing simulation plan for our isolated training lab.";

    @Test
    void sameDocumentProducesDifferentPolicyScores() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"document\":\"" + LAB_REQUEST + "\"}")
                .when().post("/moderation/public/input")
                .then()
                .statusCode(200)
                .body("status", equalTo("BLOCK"))
                .body("unsafeScore", equalTo(0.95f))
                .body("blocked", equalTo(true));

        given()
                .contentType(ContentType.JSON)
                .body("{\"document\":\"" + LAB_REQUEST + "\"}")
                .when().post("/moderation/security/input")
                .then()
                .statusCode(200)
                .body("status", equalTo("ALLOW"))
                .body("unsafeScore", equalTo(0.05f))
                .body("blocked", equalTo(false));
    }

    @Test
    void blockedInputNeverCallsTheAnsweringModel() {
        int callsBefore = ModelStubResource.assistantCalls();

        given()
                .contentType(ContentType.JSON)
                .body("{\"message\":\"" + LAB_REQUEST + "\"}")
                .when().post("/assistant/public")
                .then()
                .statusCode(422)
                .body("code", equalTo("POLICY_REJECTED"));

        assertEquals(callsBefore, ModelStubResource.assistantCalls());
    }

    @Test
    void securityPolicyAllowsTheLabRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"message\":\"" + LAB_REQUEST + "\"}")
                .when().post("/assistant/security")
                .then()
                .statusCode(200)
                .body("policy", equalTo("security"))
                .body("answer", equalTo(
                        "Run the simulation only in an isolated lab, use synthetic accounts, and alert on repeated failures."));
    }

    @Test
    void unsafeGeneratedTextIsStoppedByTheOutputGuardrail() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"message\":\"Summarize the lab result [unsafe-output]\"}")
                .when().post("/assistant/security")
                .then()
                .statusCode(422)
                .body("code", equalTo("POLICY_REJECTED"));
    }

    @Test
    void classifierFailureIsIndeterminateAndFailsClosed() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"document\":\"[classifier-down]\"}")
                .when().post("/moderation/public/input")
                .then()
                .statusCode(200)
                .body("status", equalTo("INDETERMINATE"))
                .body("unsafeScore", equalTo(null))
                .body("blocked", equalTo(true))
                .body("reason", equalTo("classifier unavailable; fail-closed policy applied"));
    }
}
