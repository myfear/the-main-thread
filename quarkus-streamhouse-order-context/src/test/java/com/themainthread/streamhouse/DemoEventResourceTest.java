package com.themainthread.streamhouse;

import static com.themainthread.streamhouse.event.InventoryStatusChanged.Status.RESERVED;
import static com.themainthread.streamhouse.event.ShippingStatusChanged.Status.DELAYED;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.themainthread.streamhouse.event.SourceHeartbeat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(DemoEventResourceTest.DevProfile.class)
class DemoEventResourceTest {
    public static class DevProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "dev";
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("kafka-streams.state.dir", "target/kafka-streams-demo-test");
        }
    }

    @BeforeEach
    void ready() {
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() ->
                given().get("/q/health/ready").then().statusCode(200));
    }

    @Test
    void typedPublishersDriveKafkaAndTheQueryStore() {
        long id = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
        given().contentType("application/json").body(Fixtures.checkout(id)).post("/_demo/order-placed").then().statusCode(204);
        given().contentType("application/json").body(Fixtures.inventory(id, 1, RESERVED)).post("/_demo/inventory").then().statusCode(204);
        given().contentType("application/json").body(Fixtures.shipping(id, 2, DELAYED)).post("/_demo/shipping").then().statusCode(204);
        given().contentType("application/json").body(new SourceHeartbeat("shipping", Instant.now()))
                .post("/_demo/heartbeat").then().statusCode(204);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> given().get("/orders/" + id + "/context").then()
                .statusCode(200).body("complete", equalTo(true), "shipping.status", equalTo("DELAYED"),
                        "sourceSignals.shipping", equalTo("RECENT"), "catchUpVerified", equalTo(false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{broken",
            "{\"eventId\":\"0d40b6f3-6a0c-4cdd-91b8-a81ab039609b\",\"orderId\":1042,\"revision\":1,\"status\":\"FLYING\",\"occurredAt\":\"2026-10-27T13:15:00Z\"}",
            "{\"eventId\":\"0d40b6f3-6a0c-4cdd-91b8-a81ab039609b\",\"orderId\":1042,\"revision\":0,\"status\":\"PACKED\",\"occurredAt\":\"2026-10-27T13:15:00Z\"}"})
    void invalidEventsReturnTheSameErrorShape(String json) {
        given().contentType("application/json").body(json).post("/_demo/shipping").then()
                .statusCode(400).body("code", equalTo("INVALID_REQUEST"));
        given().get("/q/health/ready").then().statusCode(200);
    }

    @Test
    void futureHeartbeatIsRejectedBeforePublishing() {
        given().contentType("application/json").body(new SourceHeartbeat("shipping", Instant.now().plusSeconds(60)))
                .post("/_demo/heartbeat").then().statusCode(400).body("code", equalTo("INVALID_REQUEST"));
    }
}
