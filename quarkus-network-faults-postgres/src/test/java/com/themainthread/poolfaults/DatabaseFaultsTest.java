package com.themainthread.poolfaults;

import static io.quarkiverse.networkfaults.Moment.onNextWrite;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import io.quarkiverse.networkfaults.ActiveFault;
import io.quarkiverse.networkfaults.NetworkFaults;
import io.quarkiverse.networkfaults.Target;
import io.quarkiverse.networkfaults.junit.NetworkFaultsProfile;
import io.quarkiverse.networkfaults.junit.Stopwatch;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(NetworkFaultsProfile.class)
class DatabaseFaultsTest {
    private static final Logger LOG = Logger.getLogger(DatabaseFaultsTest.class);

    @Inject
    NetworkFaults faults;

    @Test
    void holdsEstablishedConnectionsUntilThePoolRunsOut() throws Exception {
        when().post("/lab/pool/warm").then().statusCode(200);
        Target database = faults.target(DataSource.class);

        try (var callers = Executors.newFixedThreadPool(2)) {
            try (ActiveFault fault = database.hangs(onNextWrite())) {
                var first = callers.submit(() -> when().get("/quotes/count").statusCode());
                var second = callers.submit(() -> when().get("/quotes/count").statusCode());
                database.traffic().awaitHeldConnections(2, Duration.ofSeconds(3));

                long elapsed = Stopwatch.millis(() -> when().get("/quotes/count").then()
                        .statusCode(503).body("cause", equalTo("SQLException")));
                LOG.infof("Pool acquisition failed after %d ms with two connections held by the proxy", elapsed);
                assertTrue(elapsed >= 750 && elapsed < 3_000, "acquisition wait was " + elapsed + " ms");
                when().get("/quotes/preview").then().statusCode(200);

                fault.end();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void resolvesTheEntityManagerToTheDatabaseAndCleansUpAfterAReset() {
        when().post("/lab/pool/warm").then().statusCode(200);
        long before = count();

        try (ActiveFault ignored = faults.target(EntityManager.class).diesMidRequest(onNextWrite())) {
            when().post("/quotes?destination=faulted").then().statusCode(503);
        }

        assertEquals(before, count());
        when().post("/quotes?destination=after-cleanup").then().statusCode(201);
        assertEquals(before + 1, count());
    }

    private static long count() {
        return Long.parseLong(when().get("/quotes/count").then().statusCode(200).extract().asString());
    }
}
