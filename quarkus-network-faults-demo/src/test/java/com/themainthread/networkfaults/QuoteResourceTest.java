package com.themainthread.networkfaults;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import io.quarkiverse.networkfaults.ActiveFault;
import io.quarkiverse.networkfaults.NetworkFaults;
import io.quarkiverse.networkfaults.Target;
import io.quarkiverse.networkfaults.junit.Attempts;
import io.quarkiverse.networkfaults.junit.NetworkFaultsProfile;
import io.quarkiverse.networkfaults.junit.Stopwatch;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(NetworkFaultsProfile.class)
@QuarkusTestResource(CarrierServer.class)
class QuoteResourceTest {

    private static final Logger LOG = Logger.getLogger(QuoteResourceTest.class);

    @Inject
    NetworkFaults faults;

    @Test
    void returnsAHealthyQuote() {
        healthyQuote("/quotes");
    }

    @Test
    void refusesConnectionsAndRecoversAfterTheFault() {
        try (ActiveFault ignored = faults.target(CarrierClient.class).unavailable()) {
            when().get("/quotes/once?destination=Berlin").then()
                    .statusCode(503)
                    .body("error", equalTo("carrier_unavailable"))
                    .body("cause", equalTo("ProcessingException"));
        }
        healthyQuote("/quotes/once");
    }

    @Test
    void boundsAHangingAttemptWithTheApplicationTimeout() {
        healthyQuote("/quotes/once");
        try (ActiveFault ignored = faults.target(CarrierClient.class).hangs()) {
            long elapsed = Stopwatch.millis(() -> when().get("/quotes/once?destination=Berlin").then()
                    .statusCode(503)
                    .body("cause", equalTo("TimeoutException")));
            LOG.infof("Single hanging attempt returned after %d ms", elapsed);
            assertTrue(elapsed >= 250 && elapsed < 900, "application timeout took " + elapsed + " ms");
        }
        healthyQuote("/quotes/once");
    }

    @Test
    void recoversFromExactlyTwoHttpFailures() {
        Target carrier = faults.target(CarrierClient.class);
        long before = carrier.traffic().httpRequestsSeen();

        try (ActiveFault ignored = carrier.http().forFirst(2).http().respondsWith(503)) {
            healthyQuote("/quotes");
            assertEquals(3, carrier.traffic().httpRequestsSeen() - before);
            Attempts.last(carrier.traffic().httpRequests(), 3)
                    .assertGapsAtLeast(Duration.ofMillis(80));
        }
    }

    @Test
    void exhaustsTheRetryBudgetOnPersistentHttpFailure() {
        Target carrier = faults.target(CarrierClient.class);
        long before = carrier.traffic().httpRequestsSeen();

        try (ActiveFault ignored = carrier.http().respondsWith(503)) {
            when().get("/quotes?destination=Berlin").then()
                    .statusCode(503)
                    .body("error", equalTo("carrier_unavailable"))
                    .body("cause", equalTo("CarrierHttpException"));
            assertEquals(3, carrier.traffic().httpRequestsSeen() - before);
        }
        healthyQuote("/quotes");
    }

    @Test
    void keepsThreeHangingAttemptsWithinTheCallerBudget() {
        healthyQuote("/quotes");
        try (ActiveFault ignored = faults.target(CarrierClient.class).hangs()) {
            long elapsed = Stopwatch.millis(() -> when().get("/quotes?destination=Berlin").then()
                    .statusCode(503)
                    .body("cause", equalTo("TimeoutException")));
            LOG.infof("Three hanging attempts returned after %d ms", elapsed);
            assertTrue(elapsed >= 950 && elapsed < 2_000, "retrying request took " + elapsed + " ms");
        }
        healthyQuote("/quotes");
    }

    @Test
    void doesNotRetryANonRetryableHttpResponse() {
        Target carrier = faults.target(CarrierClient.class);
        long before = carrier.traffic().httpRequestsSeen();

        try (ActiveFault ignored = carrier.http().respondsWith(400)) {
            when().get("/quotes?destination=Berlin").then().statusCode(503);
            assertEquals(1, carrier.traffic().httpRequestsSeen() - before);
        }
    }

    private static void healthyQuote(String endpoint) {
        when().get(endpoint + "?destination=Berlin").then()
                .statusCode(200)
                .body("destination", equalTo("Berlin"))
                .body("amount", equalTo(9.9f))
                .body("currency", equalTo("EUR"));
    }
}
