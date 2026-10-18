package dev.mainthread.openui;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class GenerationResourceTest {
    @Test
    void mapsTextChunksAndClosesTheRunInOrder() {
        String id = UUID.randomUUID().toString();
        String body = given().contentType("application/json").body(Map.of("runId", id, "notes", "A workshop"))
                .post("/api/generate").then().statusCode(200).contentType("text/event-stream")
                .extract().asString();
        assertTrue(body.indexOf("RUN_STARTED") < body.indexOf("TEXT_MESSAGE_START"));
        assertTrue(body.indexOf("TEXT_MESSAGE_START") < body.indexOf("TEXT_MESSAGE_CONTENT"));
        assertTrue(body.indexOf("TEXT_MESSAGE_END") < body.indexOf("RUN_FINISHED"));
        assertEquals(2, body.split("TEXT_MESSAGE_CONTENT", -1).length - 1);
        assertTrue(body.contains("test-agent"));
        assertTrue(body.contains(id));
        given().delete("/api/generate/" + id).then().statusCode(200).body("cancelled", org.hamcrest.Matchers.is(false));
    }

    @Test
    void rejectsInvalidInputsBeforeOpeningBob() {
        for (String notes : new String[] { "", " ", "x".repeat(12_001) }) {
            given().contentType("application/json").body(Map.of("runId", UUID.randomUUID().toString(), "notes", notes))
                    .post("/api/generate").then().statusCode(400);
        }
        given().contentType("application/json").body(Map.of("runId", "wrong", "notes", "Workshop"))
                .post("/api/generate").then().statusCode(400);
    }

    @Test
    void returnsAnErrorEventWithoutProviderDetails() {
        given().contentType("application/json").body(Map.of("runId", UUID.randomUUID().toString(), "notes", "provider-failure"))
                .post("/api/generate").then().statusCode(200)
                .body(containsString("RUN_ERROR"), not(containsString("provider-private-detail")), not(containsString("RUN_FINISHED")));
    }

    @Test
    void cancellationReleasesTheSingleRunSlot() throws Exception {
        FakeBobFactory.waiting = new CountDownLatch(1);
        String id = UUID.randomUUID().toString();
        CompletableFuture<String> response = CompletableFuture.supplyAsync(() -> given().contentType("application/json")
                .body(Map.of("runId", id, "notes", "wait-until-cancelled")).post("/api/generate").asString());
        try {
            assertTrue(FakeBobFactory.waiting.await(5, TimeUnit.SECONDS));
            given().contentType("application/json").body(Map.of("runId", UUID.randomUUID().toString(), "notes", "Another"))
                    .post("/api/generate").then().statusCode(409);
            given().delete("/api/generate/" + id).then().statusCode(200).body("cancelled", org.hamcrest.Matchers.is(true));
            assertTrue(response.get(5, TimeUnit.SECONDS).contains("RUN_ERROR"));
            given().contentType("application/json").body(Map.of("runId", UUID.randomUUID().toString(), "notes", "Next"))
                    .post("/api/generate").then().statusCode(200).body(containsString("RUN_FINISHED"));
        } finally {
            given().delete("/api/generate/" + id);
        }
    }
}
