package com.themainthread.bazel;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
public class GreetingResourceTest {

    @Test
    void returnsJsonGreeting() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("message", equalTo("Hello from Bazel"));
    }
}
