package com.themainthread.shapeshift;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(OllamaStub.class)
class IntentResourceTest {
    @TestHTTPResource
    URI base;
    @Inject
    ShapeConfig config;

    @Test
    void callsRealLangChainAdapterAndReturnsParsedCard() {
        given().contentType("application/json").body("{\"text\":\"25 min focus\",\"useCache\":false}")
                .post("/api/intent").then().statusCode(200)
                .body("source", equalTo("ollama"), "prediction.intent", equalTo("TIMER"),
                        "prediction.probabilities.TIMER", equalTo(0.94f), "card.fields.seconds", equalTo(1500));
    }

    @Test
    void cachesOnlySuccessfulPredictions() {
        given().contentType("application/json").body("{\"text\":\"7 minutes cache test\"}").post("/api/intent").then()
                .statusCode(200);
        given().contentType("application/json").body("{\"text\":\"7 minutes cache test\"}").post("/api/intent").then()
                .body("prediction.cached", equalTo(true), "prediction.inferenceMillis", equalTo(0));
    }

    @Test
    void rejectsMalformedModelAnswerWithoutPretendingItWasANote() {
        given().contentType("application/json").body("{\"text\":\"malformed input\",\"useCache\":false}")
                .post("/api/intent").then().statusCode(503);
    }

    @Test
    void validatesInputAndSkipsEmptyInference() {
        given().contentType("application/json").body("{}").post("/api/intent").then().statusCode(400);
        given().contentType("application/json").body("{\"text\":\"" + "x".repeat(501) + "\"}").post("/api/intent").then()
                .statusCode(400);
        given().contentType("application/json").body("{\"text\":\"\"}").post("/api/intent").then().statusCode(200)
                .body("source", equalTo("empty"));
    }

    @Test
    void manualSelectionDoesNotCallTheModel() {
        int before = modelRequests();
        given().contentType("application/json").body("{\"text\":\"split 10 between 3\",\"intent\":\"SPLIT\"}")
                .post("/api/parse").then().statusCode(200)
                .body("fields.perPerson", equalTo("3.33"), "fields.remainderCents", equalTo(1));
        given().contentType("application/json").body("{\"text\":\"anything\",\"intent\":\"NONE\"}").post("/api/parse").then()
                .statusCode(400);
        assertEquals(before, modelRequests());
    }

    @Test
    void refusesAQueueOfOverlappingModelCalls() throws Exception {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(base.resolve("api/intent")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"slow 25 minutes\",\"useCache\":false}")).build();
        var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        // Both requests overlap inside a 600 ms stub response. Order is immaterial.
        var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var statuses = java.util.Set.of(first.get().statusCode(), second.get().statusCode());
        assertEquals(java.util.Set.of(200, 429), statuses);
    }

    @Test
    void servesBundledFrontend() {
        given().get("/").then().statusCode(200).body(containsString("Give it a shape."), containsString("/static/"));
    }

    @Test
    void unfinishedInputHasNoCardOrInventedModelScore() {
        int before = modelRequests();
        for (String text : new String[] { "remind me", "dinner with", "split", "buy", "set a", "I want to" }) {
            given().contentType("application/json").body(java.util.Map.of("text", text, "useCache", false))
                    .post("/api/intent").then().statusCode(200)
                    .body("source", equalTo("input-gate"), "ready", equalTo(false),
                            "prediction.intent", equalTo("NONE"), "prediction.probabilities.size()", equalTo(0),
                            "card", org.hamcrest.Matchers.nullValue());
        }
        assertEquals(before, modelRequests(), "Unfinished input must not reach the Ollama endpoint");
    }

    @Test
    void highConfidenceStillNeedsUsableTimerData() {
        given().contentType("application/json").body("{\"text\":\"start focus without a duration\",\"useCache\":false}")
                .post("/api/intent").then().statusCode(200)
                .body("prediction.intent", equalTo("TIMER"), "ready", equalTo(false),
                        "source", equalTo("ollama"), "card", org.hamcrest.Matchers.nullValue());
    }

    private int modelRequests() {
        return Integer.parseInt(given().get(config.ollamaUrl() + "/requests").then().statusCode(200).extract().asString());
    }
}
