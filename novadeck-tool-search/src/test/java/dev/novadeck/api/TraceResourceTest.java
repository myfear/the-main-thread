package dev.novadeck.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
class TraceResourceTest {

    @Test
    void recentTraceEndpointReturnsJsonArray() {
        String response = RestAssured.when()
                .get("/api/trace/recent")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .extract()
                .asString();
        
        assertEquals("[]", response);
    }
}
