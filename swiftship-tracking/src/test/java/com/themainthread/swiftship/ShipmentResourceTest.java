package com.themainthread.swiftship;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ShipmentResourceTest {

    @Test
    void listsSeededShipments() {
        given()
                .when().get("/api/shipments")
                .then()
                .statusCode(200)
                .body("$", hasSize(3))
                .body("[0].trackingNumber", equalTo("SWIFT-1001"))
                .body("[2].status", equalTo("DELIVERED"));
    }

    @Test
    void returnsShipmentByTrackingNumber() {
        given()
                .when().get("/api/shipments/SWIFT-1002")
                .then()
                .statusCode(200)
                .body("destination", equalTo("Paris, France"))
                .body("status", equalTo("OUT_FOR_DELIVERY"))
                .body("currentLocation", equalTo("Paris Depot"));
    }

    @Test
    void returnsNotFoundForUnknownTrackingNumber() {
        given()
                .when().get("/api/shipments/SWIFT-9999")
                .then()
                .statusCode(404);
    }

    @Test
    void summarizesShipmentStatuses() {
        given()
                .when().get("/api/shipments/summary")
                .then()
                .statusCode(200)
                .body("total", equalTo(3))
                .body("byStatus.IN_TRANSIT", equalTo(1))
                .body("byStatus.OUT_FOR_DELIVERY", equalTo(1))
                .body("byStatus.DELIVERED", equalTo(1));
    }

    @Test
    void reportsDatabaseReadiness() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
