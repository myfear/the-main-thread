package org.acme;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RuntimeResourceTest {

    @Test
    void reportsTheJvmRunningTheApplication() {
        given()
                .when().get("/runtime")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON)
                .body("javaVersion", equalTo(System.getProperty("java.version")))
                .body("javaRuntimeVersion", equalTo(System.getProperty("java.runtime.version")))
                .body("javaVendor", equalTo(System.getProperty("java.vendor")));
    }
}
