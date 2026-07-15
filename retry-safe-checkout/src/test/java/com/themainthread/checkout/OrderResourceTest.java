package com.themainthread.checkout;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;

@QuarkusTest
class OrderResourceTest {

    private static final String BODY = """
            {"sku":"keyboard-1","quantity":1}
            """;

    @Test
    void sameKeyReplaysTheOriginalResponse() {
        CheckoutStats before = stats();
        String key = UUID.randomUUID().toString();

        Response first = postOrder(key, BODY)
                .then()
                .statusCode(201)
                .header("Idempotent-Replayed", nullValue())
                .extract().response();

        Response replay = postOrder(key, BODY)
                .then()
                .statusCode(201)
                .header("Idempotent-Replayed", equalTo("true"))
                .extract().response();

        assertEquals(first.asString(), replay.asString());
        assertEquals(first.header("Location"), replay.header("Location"));

        CheckoutStats after = stats();
        assertEquals(before.orders() + 1, after.orders());
        assertEquals(before.fulfillmentDispatches() + 1, after.fulfillmentDispatches());
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        String key = UUID.randomUUID().toString();

        postOrder(key, BODY).then().statusCode(201);

        postOrder(key, """
                {"sku":"keyboard-1","quantity":2}
                """)
                .then()
                .statusCode(422)
                .contentType("application/problem+json")
                .body("status", equalTo(422))
                .body("title", equalTo("Idempotency-Key reused with a different payload"));
    }

    @Test
    void concurrentRetryGetsConflictThenCanReplay() throws Exception {
        CheckoutStats before = stats();
        String key = UUID.randomUUID().toString();

        CompletableFuture<Response> firstCall = CompletableFuture.supplyAsync(() -> postOrder(key, BODY));
        waitUntilProcessing(Duration.ofSeconds(5));

        postOrder(key, BODY)
                .then()
                .statusCode(409)
                .contentType("application/problem+json")
                .body("status", equalTo(409));

        firstCall.get(5, TimeUnit.SECONDS).then().statusCode(201);

        postOrder(key, BODY)
                .then()
                .statusCode(201)
                .header("Idempotent-Replayed", equalTo("true"));

        CheckoutStats after = stats();
        assertEquals(before.orders() + 1, after.orders());
        assertEquals(before.fulfillmentDispatches() + 1, after.fulfillmentDispatches());
    }

    @Test
    void missingKeyIsRejectedOnTheAnnotatedEndpoint() {
        given()
                .contentType("application/json")
                .body(BODY)
                .when().post("/orders")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    private Response postOrder(String key, String body) {
        return given()
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .body(body)
                .when().post("/orders");
    }

    private CheckoutStats stats() {
        return given()
                .when().get("/orders/stats")
                .then().statusCode(200)
                .extract().as(CheckoutStats.class);
    }

    private void waitUntilProcessing(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (stats().processing() > 0) {
                return;
            }
            Thread.sleep(25);
        }
        fail("Timed out waiting for checkout processing to start");
    }
}
