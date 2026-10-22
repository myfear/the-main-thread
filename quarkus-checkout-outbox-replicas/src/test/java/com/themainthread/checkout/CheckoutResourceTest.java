package com.themainthread.checkout;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

@QuarkusTest
class CheckoutResourceTest {

    @Inject
    OutboxPublisher publisher;

    @Inject
    CrashSwitch crashes;

    @BeforeEach
    void resetCrashSwitch() {
        crashes.reset();
    }

    @Test
    void checkoutReturnsTheOrderThenFulfillmentArrives() {
        int orderId = given()
                .contentType(ContentType.JSON)
                .body("{\"sku\":\"monitor-1\",\"quantity\":2}")
                .when().post("/orders")
                .then()
                .statusCode(201)
                .header("Location", containsString("/orders/"))
                .body("sku", equalTo("monitor-1"))
                .body("quantity", equalTo(2))
                .body("status", equalTo("ACCEPTED"))
                .extract().path("id");

        given().when().get("/orders/" + orderId)
                .then()
                .statusCode(200)
                .body("id", equalTo(orderId))
                .body("sku", equalTo("monitor-1"));

        given().when().get("/outbox")
                .then()
                .statusCode(200)
                .body("find { it.aggregateid == '" + orderId + "' }.type", equalTo("OrderPlaced"))
                .body("find { it.aggregateid == '" + orderId + "' }.aggregatetype", equalTo("Order"));

        publisher.publishPending();

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> given()
                .when().get("/fulfillments/order/" + orderId)
                .then()
                .statusCode(200)
                .body("orderId", equalTo(orderId))
                .body("sku", equalTo("monitor-1"))
                .body("quantity", equalTo(2))
                .body("eventId", notNullValue()));

        given().when().get("/outbox")
                .then()
                .statusCode(200)
                .body("find { it.aggregateid == '" + orderId + "' }.publishedAt", notNullValue());
    }

    @Test
    void unknownOrderIsNotFound() {
        given().when().get("/orders/999999")
                .then()
                .statusCode(404);
    }
}
