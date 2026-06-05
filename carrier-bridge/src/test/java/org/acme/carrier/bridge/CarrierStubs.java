package org.acme.carrier.bridge;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

final class CarrierStubs {

    private CarrierStubs() {
    }

    static void reset(WireMock wireMock) {
        wireMock.resetMappings();
        wireMock.resetRequests();
        wireMock.resetScenarios();
    }

    static void stubSuccess(WireMock wireMock, String trackingId) {
        wireMock.register(get(urlEqualTo(path(trackingId)))
                .willReturn(okJson(successBody(trackingId, "IN_TRANSIT", "2026-06-05T12:30:00Z"))));
    }

    static void stubSlow(WireMock wireMock, String trackingId) {
        wireMock.register(get(urlEqualTo(path(trackingId)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(450)
                        .withBody(successBody(trackingId, "IN_TRANSIT", "2026-06-05T12:30:00Z"))));
    }

    static void stubUnavailableThenSuccess(WireMock wireMock, String trackingId) {
        String scenarioName = "carrier-retry-" + trackingId;
        wireMock.register(get(urlEqualTo(path(trackingId)))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody(errorBody("carrier unavailable")))
                .willSetStateTo("recovered"));

        wireMock.register(get(urlEqualTo(path(trackingId)))
                .inScenario(scenarioName)
                .whenScenarioStateIs("recovered")
                .willReturn(okJson(successBody(trackingId, "DELIVERED", "2026-06-05T12:31:00Z"))));
    }

    static void stubUnavailable(WireMock wireMock, String trackingId) {
        wireMock.register(get(urlEqualTo(path(trackingId)))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody(errorBody("carrier unavailable"))));
    }

    static void stubNotFound(WireMock wireMock, String trackingId) {
        wireMock.register(get(urlEqualTo(path(trackingId)))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(errorBody("unknown tracking"))));
    }

    static int requestCount(WireMock wireMock, String trackingId) {
        return wireMock.findAll(getRequestedFor(urlEqualTo(path(trackingId)))).size();
    }

    private static String path(String trackingId) {
        return "/carrier-api/tracking/" + trackingId;
    }

    private static String successBody(String trackingId, String status, String lastUpdated) {
        return """
                {
                  "trackingId": "%s",
                  "carrier": "Parcel Rocket",
                  "status": "%s",
                  "lastUpdated": "%s"
                }
                """.formatted(trackingId, status, lastUpdated);
    }

    private static String errorBody(String message) {
        return """
                {
                  "error": "%s"
                }
                """.formatted(message);
    }
}
