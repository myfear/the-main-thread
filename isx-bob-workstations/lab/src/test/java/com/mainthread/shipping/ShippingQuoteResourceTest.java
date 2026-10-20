package com.mainthread.shipping;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ShippingQuoteResourceTest {

    @Test
    void returnsTheStandardQuote() {
        given()
                .when().get("/shipping/quote")
                .then()
                .statusCode(200)
                .body("service", equalTo("standard"))
                .body("price", equalTo(12));
    }
}
