package com.themainthread.hardened;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class StatusResourceTest {

    @Test
    void returnsRuntimeStatus() {
        given()
                .when().get("/status")
                .then()
                .statusCode(200)
                .body("service", equalTo("hardened-quarkus-jib"))
                .body("status", equalTo("ready"))
                .body("javaFeatureVersion", equalTo(25));
    }

    @Test
    void exposesReadiness() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
