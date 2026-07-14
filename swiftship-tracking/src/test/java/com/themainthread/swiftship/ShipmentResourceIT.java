package com.themainthread.swiftship;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class ShipmentResourceIT {

    @Test
    void exercisesRepresentativeStartupWorkload() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));

        given()
                .when().get("/api/shipments")
                .then()
                .statusCode(200)
                .body("$", hasSize(3))
                .body("[0].trackingNumber", equalTo("SWIFT-1001"));

        given()
                .when().get("/api/shipments/SWIFT-1002")
                .then()
                .statusCode(200)
                .body("status", equalTo("OUT_FOR_DELIVERY"));

        given()
                .when().get("/api/shipments/summary")
                .then()
                .statusCode(200)
                .body("total", equalTo(3))
                .body("byStatus.DELIVERED", equalTo(1));
    }
}
