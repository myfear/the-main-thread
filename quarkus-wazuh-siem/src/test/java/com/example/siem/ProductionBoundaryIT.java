package com.example.siem;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusIntegrationTest;

import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
class ProductionBoundaryIT {
    @Test
    void packagedAppDoesNotAcceptDevelopmentAccounts() {
        given().auth().preemptive().basic("alice", "alice-dev-only").get("/api/profile")
                .then().statusCode(anyOf(is(401), is(403)));
        given().auth().preemptive().basic("operator", "operator-dev-only").post("/api/admin/export")
                .then().statusCode(anyOf(is(401), is(403)));
    }
}
