package com.pixel;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.pixel.service.ConversionGate;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

@QuarkusTest
class ConversionGateTest {
    @Inject
    ConversionGate gate;

    @Test
    void rejectsOverloadAndReleasesAfterAnException() throws Exception {
        byte[] input = ImageFilterResourceTest.image(16, 16, "png");
        try (var first = gate.enter(); var second = gate.enter()) {
            given().multiPart("image", "input.png", input).post("/api/filter/pixelate").then()
                    .statusCode(503).header("Retry-After", "1");
        }
        assertThrows(IllegalStateException.class, () -> {
            try (var permit = gate.enter()) {
                throw new IllegalStateException("simulated conversion failure");
            }
        });
        given().multiPart("image", "input.png", input).post("/api/filter/pixelate").then().statusCode(200);
    }

    @Test
    void closingAPermitTwiceDoesNotCreateExtraCapacity() {
        var permit = gate.enter();
        permit.close();
        permit.close();
        try (var first = gate.enter(); var second = gate.enter()) {
            assertThrows(WebApplicationException.class, gate::enter);
        }
    }
}
