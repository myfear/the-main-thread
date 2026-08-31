package com.acme.catalog.adapter.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ProductResourceTest {

    @Test
    void returnsKnownProduct() {
        given()
                .when().get("/products/sku-1")
                .then()
                .statusCode(200)
                .body("sku", equalTo("sku-1"))
                .body("name", equalTo("Mechanical Keyboard"))
                .body("price", equalTo(129.00F))
                .body("currency", equalTo("EUR"));
    }

    @Test
    void returnsNotFoundForUnknownProduct() {
        given()
                .when().get("/products/missing")
                .then()
                .statusCode(404);
    }
}
