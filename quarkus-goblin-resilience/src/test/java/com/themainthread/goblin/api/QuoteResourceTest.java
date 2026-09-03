package com.themainthread.goblin.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class QuoteResourceTest {

    @Test
    void returnsFallbackQuoteWhenGoblinBreaksInventory() {
        given()
                .when().get("/quotes/sku-1")
                .then()
                .statusCode(200)
                .body("sku", equalTo("sku-1"))
                .body("service", equalTo("STANDARD"))
                .body("available", equalTo(0))
                .body("source", equalTo("fallback"));
    }

    @Test
    void targetsTheInternalResourceDirectly() {
        given()
                .when().get("/internal/inventory/sku-1")
                .then()
                .statusCode(503)
                .body(equalTo("Inventory unavailable (Goblin test)"));
    }
}
