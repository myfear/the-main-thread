package com.catalogapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import com.catalogapi.json.ProductInput;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class ProductResourceTest {

    @Test
    void listReturnsSeededProducts() {
        given().when()
                .get("/products")
                .then()
                .statusCode(200)
                .body("$", hasSize(4));
    }

    @Test
    void getByIdReturnsProduct() {
        given().when()
                .get("/products/1")
                .then()
                .statusCode(200)
                .body("sku", equalTo("SKU-001"))
                .body("name", equalTo("Mechanical Keyboard"));
    }

    @Test
    void createAndUpdateProduct() {
        ProductInput input = new ProductInput("SKU-NEW", "Test Lamp", 1999, "lighting");

        int createdId = given().contentType(ContentType.JSON)
                .body(input)
                .when()
                .post("/products")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract()
                .path("id");

        given().contentType(ContentType.JSON)
                .body(new ProductInput("SKU-NEW", "Test Lamp Pro", 2499, "lighting"))
                .when()
                .put("/products/" + createdId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Test Lamp Pro"))
                .body("priceCents", equalTo(2499));
    }
}
