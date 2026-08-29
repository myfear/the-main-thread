package com.themainthread.pricing;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.inject.Inject;

import org.apache.fory.BaseFory;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PricingResourceTest {

    @Inject
    BaseFory fory;

    @Test
    void acceptsAndReturnsTheRegisteredForyContract() {
        byte[] request = fory.serialize(SampleSnapshots.sample());

        byte[] response = given()
                .contentType("application/fory")
                .accept("application/fory")
                .body(request)
                .when()
                .post("/internal/pricing/quote")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();

        QuoteDecision quote = (QuoteDecision) fory.deserialize(response);

        assertFalse(response.length == 0);
        assertEquals("quote-20260829-001", quote.snapshotId());
        assertEquals(15_396, quote.subtotalCents());
        assertEquals(799, quote.shippingCents());
        assertEquals(16_195, quote.totalCents());
        assertEquals(2, quote.deliveryDays());
    }

    @Test
    void keepsJsonOutOfTheInternalContract() {
        given()
                .contentType("application/json")
                .body("{\"snapshotId\":\"quote-20260829-001\"}")
                .when()
                .post("/internal/pricing/quote")
                .then()
                .statusCode(415);
    }

    @Test
    void keepsThePublicCatalogEndpointReadable() {
        given()
                .accept("application/json")
                .when()
                .get("/catalog/snapshots/sample")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("snapshotId", org.hamcrest.Matchers.equalTo("quote-20260829-001"));
    }
}
