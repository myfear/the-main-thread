package com.themainthread.networkfaults;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
@QuarkusTestResource(CarrierServer.class)
class QuoteResourceIT {

    @Test
    void packagedApplicationCallsTheCarrierWithoutTheFaultExtension() throws IOException {
        when().get("/quotes?destination=Berlin").then()
                .statusCode(200)
                .body("amount", equalTo(9.9f))
                .body("currency", equalTo("EUR"));

        try (var libraries = Files.walk(Path.of("target/quarkus-app/lib/main"))) {
            assertTrue(libraries.noneMatch(path -> path.getFileName().toString().contains("network-faults")),
                    "test-scoped fault injection must not ship in the application");
        }
    }
}
