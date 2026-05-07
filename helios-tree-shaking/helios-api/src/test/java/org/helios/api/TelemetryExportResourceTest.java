package org.helios.api;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
class TelemetryExportResourceTest {

    @Test
    void exportReturnsCsvBody() {
        RestAssured.given()
                .when()
                .get("/export")
                .then()
                .statusCode(200)
                .body(containsString("deviceId"))
                .body(containsString("sample-7"));
    }
}