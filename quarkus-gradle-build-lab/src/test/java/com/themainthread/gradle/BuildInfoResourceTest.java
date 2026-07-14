package com.themainthread.gradle;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class BuildInfoResourceTest {

    @Test
    void exposesMetadataGeneratedByGradle() {
        given()
                .when().get("/build-info")
                .then()
                .statusCode(200)
                .body("name", equalTo("quarkus-gradle-build-lab"))
                .body("version", equalTo("1.0.0-SNAPSHOT"));
    }
}
