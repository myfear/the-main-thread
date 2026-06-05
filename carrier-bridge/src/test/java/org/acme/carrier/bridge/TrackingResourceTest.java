package org.acme.carrier.bridge;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@ConnectWireMock
@TestHTTPEndpoint(TrackingResource.class)
class TrackingResourceTest {

    private static final String REST_CLIENT_LOG_CATEGORY = "org.jboss.resteasy.reactive.client.logging";

    private static Logger restClientLogger;
    private static InMemoryLogHandler logHandler;

    WireMock wiremock;

    @BeforeAll
    static void installLogHandler() {
        restClientLogger = Logger.getLogger(REST_CLIENT_LOG_CATEGORY);
        logHandler = new InMemoryLogHandler();
        restClientLogger.addHandler(logHandler);
        restClientLogger.setLevel(Level.FINE);
    }

    @AfterAll
    static void removeLogHandler() {
        if (restClientLogger != null && logHandler != null) {
            restClientLogger.removeHandler(logHandler);
        }
    }

    @BeforeEach
    void resetState() {
        CarrierStubs.reset(wiremock);
        logHandler.clear();
    }

    @Test
    void returnsTrackingStatus() {
        CarrierStubs.stubSuccess(wiremock, "TRACK-123");

        given()
                .when().get("/TRACK-123")
                .then()
                .statusCode(200)
                .body("trackingId", equalTo("TRACK-123"))
                .body("carrier", equalTo("Parcel Rocket"))
                .body("status", equalTo("IN_TRANSIT"))
                .body("lastUpdated", equalTo("2026-06-05T12:30:00Z"));
    }

    @Test
    void returnsGatewayTimeoutWhenCarrierIsSlow() {
        CarrierStubs.stubSlow(wiremock, "TRACK-SLOW");

        given()
                .when().get("/TRACK-SLOW")
                .then()
                .statusCode(504)
                .body("code", equalTo("carrier_timeout"))
                .body("message", equalTo("Carrier API did not respond before the outbound read timeout."))
                .body("downstreamStatus", nullValue());
    }

    @Test
    void retriesOnceAndSucceedsAfterTransientFailure() {
        CarrierStubs.stubUnavailableThenSuccess(wiremock, "TRACK-RETRY");

        given()
                .when().get("/TRACK-RETRY")
                .then()
                .statusCode(200)
                .body("trackingId", equalTo("TRACK-RETRY"))
                .body("status", equalTo("DELIVERED"));

        assertEquals(2, CarrierStubs.requestCount(wiremock, "TRACK-RETRY"));
    }

    @Test
    void returnsServiceUnavailableAfterPermanentCarrierFailure() {
        CarrierStubs.stubUnavailable(wiremock, "TRACK-DOWN");

        given()
                .when().get("/TRACK-DOWN")
                .then()
                .statusCode(503)
                .body("code", equalTo("carrier_unavailable"))
                .body("message", equalTo("Carrier API is temporarily unavailable."))
                .body("downstreamStatus", equalTo(503));

        assertEquals(2, CarrierStubs.requestCount(wiremock, "TRACK-DOWN"));
    }

    @Test
    void returnsNotFoundWhenCarrierDoesNotKnowTrackingId() {
        CarrierStubs.stubNotFound(wiremock, "TRACK-MISSING");

        given()
                .when().get("/TRACK-MISSING")
                .then()
                .statusCode(404)
                .body("code", equalTo("tracking_not_found"))
                .body("message", equalTo("Carrier API could not find tracking ID 'TRACK-MISSING'."))
                .body("downstreamStatus", equalTo(404));

        assertEquals(1, CarrierStubs.requestCount(wiremock, "TRACK-MISSING"));
    }

    @Test
    void masksSensitiveHeadersInRestClientLogs() {
        CarrierStubs.stubSuccess(wiremock, "TRACK-LOGS");

        given()
                .when().get("/TRACK-LOGS")
                .then()
                .statusCode(200);

        String logs = logHandler.joinedMessages();
        assertFalse(logs.contains(CarrierAuthFilter.DEMO_BEARER_TOKEN));
        assertFalse(logs.contains(CarrierAuthFilter.DEMO_API_KEY));
        assertTrue(logs.contains("Authorization"));
        assertTrue(logs.contains("X-Carrier-Key"));
        assertTrue(logs.contains("<hidden>"));
    }
}
