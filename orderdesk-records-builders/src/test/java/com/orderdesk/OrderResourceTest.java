package com.orderdesk;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class OrderResourceTest {

    @Test
    void shouldListProducts() {
        given()
                .when()
                .get("/products")
                .then()
                .statusCode(200)
                .body("size()", is(2))
                .body("[0].name", is("Mechanical Keyboard"));
    }

    @Test
    void shouldReturnSampleOrder() {
        given()
                .when()
                .get("/orders/sample")
                .then()
                .statusCode(200)
                .body("orderId", is("ORD-2026-001"))
                .body("customerId", is("customer-42"))
                .body("products.size()", is(2))
                .body("status", is("READY"))
                .body("currency", is("EUR"))
                .body("createdAt", notNullValue());
    }

    @Test
    void shouldCreateOrderFromRequest() {
        String payload = """
                {
                  "customerId": "customer-77",
                  "productIds": [1],
                  "shippingAddress": "Tech Street 42"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/orders")
                .then()
                .statusCode(200)
                .body("customerId", is("customer-77"))
                .body("shippingAddress", is("Tech Street 42"))
                .body("products.size()", is(1))
                .body("status", is("READY"))
                .body("total", notNullValue())
                .body("fraudScore", greaterThan(0));
    }

    @Test
    void shouldRejectInvalidRequest() {
        String payload = """
                {
                  "customerId": "",
                  "productIds": [],
                  "shippingAddress": ""
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/orders")
                .then()
                .statusCode(400)
                .body("violations", not(empty()));
    }
}
