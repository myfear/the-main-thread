package com.themainthread.checkout;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class CheckoutResourceTest {
    @Test
    void checkoutCommitsAnEventWithoutClaimingDelivery() {
        int id = given().contentType(ContentType.JSON)
                .body("{\"sku\":\"monitor-1\",\"quantity\":2}")
                .post("/orders").then().statusCode(201)
                .header("Location", containsString("/orders/"))
                .body("status", equalTo("ACCEPTED")).extract().path("id");
        given().get("/orders/" + id).then().statusCode(200).body("quantity", equalTo(2));
        Map<String, Object> event = given().get("/outbox").then().statusCode(200)
                .extract().path("find { it.aggregateid == '" + id + "' }");
        assertFalse(event.containsKey("publishedAt"));
        given().get("/fulfillments/order/" + id).then().statusCode(404);
    }

    @Test
    void unknownOrderIsNotFound() {
        given().get("/orders/999999").then().statusCode(404);
    }

    @Test
    void invalidQuantityIsRejected() {
        given().contentType(ContentType.JSON).body("{\"sku\":\"bad\",\"quantity\":0}")
                .post("/orders").then().statusCode(400);
    }

    @Test
    void obsoleteKafkaCrashIsRejected() {
        given().contentType(ContentType.JSON).header("X-Crash-After", "kafka")
                .body("{\"sku\":\"bad-crash\",\"quantity\":1}").post("/orders").then().statusCode(400);
    }

    @Test
    void dualWriteEndpointHasBeenRemoved() {
        given().contentType(ContentType.JSON).body("{\"sku\":\"old-path\",\"quantity\":1}")
                .post("/orders/dual-write").then().statusCode(405);
    }
}
