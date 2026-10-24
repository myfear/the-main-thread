package com.themainthread.shipping;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;

public abstract class ShippingQuoteContract {
    @Test
    void chargesStandardShippingBelowThreshold() {
        assertQuote("99.99", "STANDARD", 4.99f);
    }

    @Test
    void waivesStandardShippingAtThreshold() {
        assertQuote("100.00", "STANDARD", 0.00f);
    }

    @Test
    void waivesStandardShippingAboveThreshold() {
        assertQuote("100.01", "STANDARD", 0.00f);
    }

    @Test
    void chargesExpressShippingAboveThreshold() {
        assertQuote("150.00", "EXPRESS", 9.99f);
    }

    @Test
    void chargesStandardShippingForZeroSubtotal() {
        assertQuote("0.00", "STANDARD", 4.99f);
    }

    @Test
    void rejectsNegativeSubtotal() {
        assertInvalid("{\"subtotal\":-0.01,\"method\":\"STANDARD\"}");
    }

    @Test
    void rejectsMissingSubtotal() {
        assertInvalid("{\"method\":\"STANDARD\"}");
    }

    @Test
    void rejectsFractionalCents() {
        assertInvalid("{\"subtotal\":99.999,\"method\":\"STANDARD\"}");
    }

    @Test
    void rejectsMissingMethod() {
        assertInvalid("{\"subtotal\":100.00}");
    }

    private void assertQuote(String subtotal, String method, float expectedShipping) {
        given()
                .contentType(ContentType.JSON)
                .body("{\"subtotal\":" + subtotal + ",\"method\":\"" + method + "\"}")
                .when().post("/shipping-quotes")
                .then()
                .statusCode(200)
                .body("shipping", equalTo(expectedShipping))
                .body("method", equalTo(method))
                .body("currency", equalTo("EUR"));
    }

    private void assertInvalid(String request) {
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/shipping-quotes")
                .then().statusCode(400);
    }
}
