package com.themainthread.ledger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class LedgerResourceTest {

    @Inject
    JdbcCallMetrics metrics;

    @Test
    void returnsTheBalanceAndObservesTheConnectionBoundary() {
        JdbcCallMetrics.JdbcMetricsSnapshot before = metrics.snapshot();

        String accountId = given()
                .when().get("/ledger/acct-42")
                .then()
                .statusCode(200)
                .extract().path("accountId");

        JdbcCallMetrics.JdbcMetricsSnapshot after = metrics.snapshot();
        assertEquals("acct-42", accountId);
        assertTrue(after.dataSourceCalls() > before.dataSourceCalls());
        assertTrue(after.connectionCalls() > before.connectionCalls());
    }
}
