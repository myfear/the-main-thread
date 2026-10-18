package com.themainthread.demo;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ItemsResourceTest {
    @Test
    void servesItemsAndCanBeCalledAgain() {
        for (int call = 0; call < 2; call++) {
            given().when().get("/items").then().statusCode(200).body("", contains("pencil", "notebook"));
        }
    }
}
