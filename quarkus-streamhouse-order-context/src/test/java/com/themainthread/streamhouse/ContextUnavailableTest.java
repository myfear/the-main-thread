package com.themainthread.streamhouse;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.junit.jupiter.api.Test;

import com.themainthread.streamhouse.projection.StateStoreReader;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ContextUnavailableTest {
    @InjectMock
    StateStoreReader stores;

    @Test
    void storeUnavailableReturnsBounded503AndFailedReadiness() {
        when(stores.read(anyString())).thenThrow(new InvalidStateStoreException("restoring"));
        given().get("/orders/1042/context").then().statusCode(503)
                .header("Retry-After", "1").body("code", equalTo("STORE_UNAVAILABLE"));
        given().get("/q/health/ready").then().statusCode(503).body("status", equalTo("DOWN"));
    }

    @Test
    void nonPositiveOrderIdIsAStable400() {
        given().get("/orders/0/context").then().statusCode(400).body("code", equalTo("INVALID_REQUEST"));
    }
}
