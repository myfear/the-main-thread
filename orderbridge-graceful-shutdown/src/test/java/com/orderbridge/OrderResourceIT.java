package com.orderbridge;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;

@QuarkusIntegrationTest
class OrderResourceIT {

    @Test
    void shouldCompleteHandoffAgainstPackagedApp() {
        String payload = """
                {
                  "orderId": "ORD-IT-1",
                  "amountCents": 1200
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/orders/handoff")
                .then()
                .statusCode(200)
                .body("orderId", is("ORD-IT-1"))
                .body("status", is("HANDOFF_COMPLETE"))
                .body("elapsedMs", notNullValue());
    }

    @Test
    void readinessShouldBeUpInPackagedApp() {
        given()
                .when()
                .get("/q/health/ready")
                .then()
                .statusCode(200);
    }
}
