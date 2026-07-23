package com.ibm.developer.pricing;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class QuoteResourceTest {

    @Inject
    InMemoryFlagProvider inMemoryFlags;

    @AfterEach
    void removeOverride() {
        inMemoryFlags.removeFlag("pricing-engine");
    }

    @Test
    void usesSafeDefaultWhenOpenFeatureProviderIsUnavailable() {
        given()
                .queryParam("subtotal", "100.00")
                .when().get("/quotes/contoso")
                .then()
                .statusCode(200)
                .body("tenantId", is("contoso"))
                .body("pricingEngine", is("stable"))
                .body("discount", is(0.0f))
                .body("total", is(100.0f))
                .body("flagOrigin", is("quarkus.openfeature"));
    }

    @Test
    void inMemoryFlagOverridesOpenFeatureWithoutChangingBusinessCode() {
        inMemoryFlags.addFlag(Flag.builder("pricing-engine").setString("dynamic"));

        given()
                .queryParam("subtotal", "100.00")
                .when().get("/quotes/northwind")
                .then()
                .statusCode(200)
                .body("pricingEngine", is("dynamic"))
                .body("discount", is(10.0f))
                .body("total", is(90.0f))
                .body("flagOrigin", is("quarkus.in-memory"));
    }

    @Test
    void rejectsNonPositiveSubtotal() {
        given()
                .queryParam("subtotal", "0")
                .when().get("/quotes/contoso")
                .then()
                .statusCode(400);
    }
}
