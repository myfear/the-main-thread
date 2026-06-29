package dev.mainthread.incidents;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class IncidentResourceTest {

    @Test
    void findsSimilarResolvedCheckoutIncident() {
        given()
                .when()
                .post("/incidents/seed")
                .then()
                .statusCode(200)
                .body("indexed", equalTo(5));

        given()
                .contentType("application/json")
                .body("""
                        {
                          "incident": {
                            "service": "checkout-service",
                            "environment": "prod",
                            "exceptionType": "java.lang.NullPointerException",
                            "message": "Cannot invoke DiscountPolicy.percentage because policy is null while pricing cart",
                            "stackTrace": [
                              "dev.mainthread.checkout.CartPriceCalculator.applyDiscount(CartPriceCalculator.java:91)",
                              "dev.mainthread.checkout.CheckoutService.priceCart(CheckoutService.java:47)",
                              "dev.mainthread.checkout.CheckoutResource.pay(CheckoutResource.java:31)"
                            ]
                          },
                          "limit": 3,
                          "minScore": 0.60,
                          "filterService": "checkout-service",
                          "filterEnvironment": "prod",
                          "onlyResolved": true
                        }
                        """)
                .when()
                .post("/incidents/search")
                .then()
                .statusCode(200)
                .body("count", greaterThan(0))
                .body("matches[0].id", equalTo("INC-1001"))
                .body("matches[0].service", equalTo("checkout-service"));
    }

    @Test
    void rejectsIncidentsWithoutStackFrames() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "service": "checkout-service",
                          "environment": "prod",
                          "exceptionType": "java.lang.NullPointerException",
                          "message": "policy is null",
                          "stackTrace": []
                        }
                        """)
                .when()
                .post("/incidents")
                .then()
                .statusCode(400);
    }
}
