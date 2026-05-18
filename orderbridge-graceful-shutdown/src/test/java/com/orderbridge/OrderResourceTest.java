package com.orderbridge;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class OrderResourceTest {

    @Test
    void shouldReturnOrderStatus() {
        given()
                .when()
                .get("/orders/ORD-100")
                .then()
                .statusCode(200)
                .body("orderId", is("ORD-100"))
                .body("status", is("CREATED"));
    }

    @Test
    void shouldCompletePaymentHandoff() {
        String payload = """
                {
                  "orderId": "ORD-200",
                  "amountCents": 4999
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/orders/handoff")
                .then()
                .statusCode(200)
                .body("orderId", is("ORD-200"))
                .body("status", is("HANDOFF_COMPLETE"))
                .body("elapsedMs", notNullValue());
    }

    @Test
    void readinessShouldBeUpWhileRunning() {
        given()
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(200);
    }
}
