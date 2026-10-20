package com.mainthread.catalog;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class CatalogResourceTest {

    @Test
    void returnsTheCatalog() {
        given()
                .when().get("/catalog")
                .then()
                .statusCode(200)
                .body("$", hasSize(3))
                .body("[0].sku", equalTo("sku-1"))
                .body("[0].name", equalTo("Robot arm"));
    }
}
