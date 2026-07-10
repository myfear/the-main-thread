package com.themainthread.hardened;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class StatusResourceIT extends StatusResourceTest {

    @Test
    void runsOnHummingbirdJava25Runtime() {
        given()
                .when().get("/status")
                .then()
                .statusCode(200)
                .body("javaFeatureVersion", equalTo(25));
    }
}
