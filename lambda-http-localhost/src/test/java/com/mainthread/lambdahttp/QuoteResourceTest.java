package com.mainthread.lambdahttp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@TestHTTPEndpoint(QuoteResource.class)
class QuoteResourceTest {

    @Test
    void shouldPreviewQuoteThroughMockEventServer() {
        given()
                .header("X-Customer-Tier", "gold")
                .queryParam("speed", "express")
                .queryParam("weightGrams", 900)
                .when()
                .get("/lisbon")
                .then()
                .statusCode(200)
                .body("destination", equalTo("LISBON"))
                .body("speed", equalTo("express"))
                .body("customerTier", equalTo("gold"))
                .body("weightGrams", equalTo(900))
                .body("quotedCents", equalTo(990))
                .body("estimatedBusinessDays", equalTo(2))
                .body("fulfillmentRegion", equalTo("eu-central"));
    }

    @Test
    void shouldPreviewQuoteFromJsonBody() {
        String payload = """
                {
                  "destination": "Chicago",
                  "weightGrams": 1200,
                  "speed": "overnight",
                  "customerTier": "silver"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/preview")
                .then()
                .statusCode(200)
                .body("destination", equalTo("CHICAGO"))
                .body("speed", equalTo("overnight"))
                .body("customerTier", equalTo("silver"))
                .body("weightGrams", equalTo(1200))
                .body("quotedCents", equalTo(1795))
                .body("estimatedBusinessDays", equalTo(1))
                .body("fulfillmentRegion", equalTo("global-export"));
    }

    @Test
    void shouldRejectUnsupportedSpeed() {
        given()
                .queryParam("speed", "teleport")
                .when()
                .get("/porto")
                .then()
                .statusCode(400)
                .body("message", containsString("Unsupported speed"));
    }
}
