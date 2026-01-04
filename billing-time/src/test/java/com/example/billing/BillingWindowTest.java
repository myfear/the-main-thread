package com.example.billing;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class BillingWindowTest {

    @Test
    void dstBoundaryIsHandledCorrectly() {
        given()
                .queryParam("start", "2025-03-29")
                .queryParam("end", "2025-03-30")
                .queryParam("zone", "Europe/Berlin")
                .when()
                .get("/billing/window")
                .then()
                .statusCode(200)
                .body("durationHours", is(47));
    }
}