package dev.conduit.workflow.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ConduitWorkflowIT {

    private static final Pattern ROUTING_QUEUE =
            Pattern.compile("(?i)\\b(ops-general|ops-priority|ops-security)\\b");

    @Test
    void topologyHtmlReferencesHybridPipeline() {
        given().when()
                .get("/topology")
                .then()
                .statusCode(200)
                .contentType("text/html")
                .body(containsString("conduitPipeline"))
                .body(containsString("classifySeverity"))
                .body(containsString("summarizeHandoff"))
                .body(containsString("routeQueue"));
    }

    @Test
    void runCompletesPipelineAgainstLiveOllamaAndMcp() {
        String queue = given().when()
                .body("{\"rawId\":\" z-99 \",\"payloadSnippet\":\"{\\\"k\\\":1}\"}")
                .contentType("application/json")
                .post("/runs")
                .then()
                .statusCode(200)
                .extract()
                .path("targetQueue");

        assertTrue(queue != null && !queue.isBlank(), "targetQueue missing");
        assertTrue(
                ROUTING_QUEUE.matcher(queue).find(),
                "Expected ops-general | ops-priority | ops-security somewhere in output but got: " + queue);

        given().when()
                .get("/topology")
                .then()
                .statusCode(200)
                .body(containsString("classifySeverity"))
                .body(containsString("conduit_normalize_record"))
                .body(containsString("conduit_fingerprint_payload"));
    }

    @Test
    void badRequestWhenRawIdMissing() {
        given().when()
                .body("{\"payloadSnippet\":\"x\"}")
                .contentType("application/json")
                .post("/runs")
                .then()
                .statusCode(400);
    }
}
