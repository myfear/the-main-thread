package dev.themainthread.invoicerecon.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.themainthread.invoicerecon.batch.ReconciliationBatch;
import dev.themainthread.invoicerecon.mcp.support.ElicitationTestClient;
import dev.themainthread.invoicerecon.support.TestDataCleaner;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.vertx.core.json.JsonObject;

@QuarkusTest
class ElicitationDeclinedMcpTest {

    @Inject
    TestDataCleaner testDataCleaner;

    @BeforeEach
    void resetData() {
        testDataCleaner.resetReconciliationState();
    }

    @Test
    void decliningElicitationCreatesNoBatch() {
        long batchesBefore = ReconciliationBatch.count();

        try (ElicitationTestClient client = ElicitationTestClient.declineDefaults()) {
            JsonObject body = client.reconcileAcmeMay();
            assertEquals("ELICITATION_DECLINED", body.getString("outcome"));
            assertFalse(body.containsKey("batchId"));
        }

        assertEquals(batchesBefore, ReconciliationBatch.count());
    }
}
