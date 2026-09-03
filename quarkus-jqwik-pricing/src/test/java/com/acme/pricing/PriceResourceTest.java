package com.acme.pricing;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PriceResourceTest {

    @Test
    void calculatesPriceThroughHttp() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "unitPrice": 0.07,
                          "quantity": 3,
                          "discountPercent": 10
                        }
                        """)
                .when()
                .post("/prices/calculate")
                .then()
                .statusCode(200)
                .body("subtotal", equalTo(0.21F))
                .body("discountAmount", equalTo(0.02F))
                .body("total", equalTo(0.19F))
                .body("currency", equalTo("EUR"));
    }

    @Test
    void rejectsDiscountAboveMaximum() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "unitPrice": 19.99,
                          "quantity": 2,
                          "discountPercent": 51
                        }
                        """)
                .when()
                .post("/prices/calculate")
                .then()
                .statusCode(400)
                .body("code", equalTo("invalid_price_request"))
                .body("message", equalTo("discountPercent must be between 0 and 50"));
    }
}
