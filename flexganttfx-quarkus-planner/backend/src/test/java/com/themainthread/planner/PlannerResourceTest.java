package com.themainthread.planner;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PlannerResourceTest {

    @Test
    void boardReturnsOnlyBookingsThatIntersectTheWindow() {
        given()
                .queryParam("from", "2026-08-20T08:30:00Z")
                .queryParam("to", "2026-08-20T10:00:00Z")
                .when()
                .get("/api/board")
                .then()
                .statusCode(200)
                .body("doors", hasSize(5))
                .body("bookings.reference", hasItem("TRUCK-1042"))
                .body("bookings.reference", hasItem("TRUCK-2017"))
                .body("bookings.reference", not(hasItem("TRUCK-3088")));
    }

    @Test
    void scheduleAcceptsValidMove() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "doorId": "door-4",
                          "startsAt": "2026-08-20T08:00:00Z",
                          "endsAt": "2026-08-20T09:00:00Z",
                          "expectedVersion": 0
                        }
                        """)
                .when()
                .put("/api/bookings/booking-88/schedule")
                .then()
                .statusCode(200)
                .body("doorId", equalTo("door-4"))
                .body("version", equalTo(1));
    }

    @Test
    void scheduleRejectsOverlappingInterval() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "doorId": "door-5",
                          "startsAt": "2026-08-20T09:15:00Z",
                          "endsAt": "2026-08-20T10:00:00Z",
                          "expectedVersion": 0
                        }
                        """)
                .when()
                .put("/api/bookings/booking-42/schedule")
                .then()
                .statusCode(409)
                .body("code", equalTo("OVERLAPPING_BOOKING"));
    }

    @Test
    void scheduleRejectsStaleVersionAndReturnsCurrentBooking() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "doorId": "door-4",
                          "startsAt": "2026-08-20T08:00:00Z",
                          "endsAt": "2026-08-20T09:00:00Z",
                          "expectedVersion": 99
                        }
                        """)
                .when()
                .put("/api/bookings/booking-42/schedule")
                .then()
                .statusCode(409)
                .body("code", equalTo("STALE_BOOKING"))
                .body("currentBooking.reference", equalTo("TRUCK-1042"));
    }

    @Test
    void boardRejectsMissingWindowBoundary() {
        given()
                .queryParam("from", "2026-08-20T08:30:00Z")
                .when()
                .get("/api/board")
                .then()
                .statusCode(400);
    }

    @Test
    void scheduleRejectsMissingDoor() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "startsAt": "2026-08-20T08:00:00Z",
                          "endsAt": "2026-08-20T09:00:00Z",
                          "expectedVersion": 0
                        }
                        """)
                .when()
                .put("/api/bookings/booking-42/schedule")
                .then()
                .statusCode(400);
    }
}
