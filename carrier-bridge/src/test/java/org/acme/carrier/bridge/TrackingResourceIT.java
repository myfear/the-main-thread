package org.acme.carrier.bridge;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@ConnectWireMock
@TestHTTPEndpoint(TrackingResource.class)
class TrackingResourceIT {

    WireMock wiremock;

    @BeforeEach
    void resetState() {
        CarrierStubs.reset(wiremock);
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
                .body("status", equalTo("IN_TRANSIT"));
    }

    @Test
    void returnsMappedNotFound() {
        CarrierStubs.stubNotFound(wiremock, "TRACK-MISSING");

        given()
                .when().get("/TRACK-MISSING")
                .then()
                .statusCode(404)
                .body("code", equalTo("tracking_not_found"))
                .body("message", equalTo("Carrier API could not find tracking ID 'TRACK-MISSING'."))
                .body("downstreamStatus", equalTo(404));
    }
}
