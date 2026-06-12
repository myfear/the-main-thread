package dev.themainthread.invoicerecon.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.themainthread.invoicerecon.batch.ReconciliationBatch;
import dev.themainthread.invoicerecon.domain.Invoice;
import dev.themainthread.invoicerecon.mcp.support.ElicitationTestClient;
import dev.themainthread.invoicerecon.service.PostingService;
import dev.themainthread.invoicerecon.support.TestDataCleaner;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;

@QuarkusTest
class InvoiceReconciliationMcpTest {

    @Inject
    PostingService postingService;

    @Inject
    TestDataCleaner testDataCleaner;

    @BeforeEach
    void resetData() {
        testDataCleaner.resetReconciliationState();
    }

    @Test
    void toolsListIncludesReconciliationTools() {
        try (McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/mcp")
                .build()
                .connect()) {
            client.when()
                    .toolsList(page -> {
                        assertNotNull(page.findByName("reconcile_invoices"));
                        assertNotNull(page.findByName("post_reconciliation_batch"));
                    })
                    .thenAssertResults();
        }
    }

    @Test
    void clientWithoutElicitationUsesTutorialDefaultPolicy() {
        long batchesBefore = ReconciliationBatch.count();

        try (McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/mcp")
                .build()
                .connect()) {
            client.when()
                    .toolsCall(
                            "reconcile_invoices",
                            Map.of(
                                    "supplierId", "ACME",
                                    "from", LocalDate.of(2026, 5, 1).toString(),
                                    "to", LocalDate.of(2026, 5, 31).toString()),
                            response -> {
                                assertFalse(response.isError());
                                JsonObject body = new JsonObject(response.firstContent().asText().text());
                                assertEquals("COMPLETED", body.getString("outcome"));
                                assertEquals(32, body.getInteger("processed"));
                                assertEquals(24, body.getInteger("matched"));
                                assertEquals(8, body.getInteger("exceptions"));
                                assertEquals("READY_FOR_REVIEW", body.getString("status"));
                            })
                    .thenAssertResults();
        }

        assertEquals(batchesBefore + 1, ReconciliationBatch.count());
        assertEquals(0, Invoice.count("posted", true));
    }

    @Test
    void clientWithoutElicitationResolvesAcmeSuppliesAlias() {
        try (McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/mcp")
                .build()
                .connect()) {
            client.when()
                    .toolsCall(
                            "reconcile_invoices",
                            Map.of(
                                    "supplierId", "acme-supplies",
                                    "from", LocalDate.of(2026, 5, 1).toString(),
                                    "to", LocalDate.of(2026, 5, 31).toString(),
                                    "postMatchedInvoices", false),
                            response -> {
                                assertFalse(response.isError());
                                JsonObject body = new JsonObject(response.firstContent().asText().text());
                                assertEquals("COMPLETED", body.getString("outcome"));
                                assertEquals(32, body.getInteger("processed"));
                                assertEquals("ACME", body.getString("supplier"));
                            })
                    .thenAssertResults();
        }
    }

    @Test
    void clientWithoutElicitationAcceptsInlinePolicy() {
        try (McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/mcp")
                .build()
                .connect()) {
            client.when()
                    .toolsCall(
                            "reconcile_invoices",
                            Map.of(
                                    "supplierId", "ACME",
                                    "from", LocalDate.of(2026, 5, 1).toString(),
                                    "to", LocalDate.of(2026, 5, 31).toString(),
                                    "maximumVariancePercent", 2.5,
                                    "defaultCostCenter", "FIN-OPERATIONS",
                                    "postMatchedInvoices", false,
                                    "missingGoodsReceiptAction", "FLAG_FOR_REVIEW"),
                            response -> {
                                assertFalse(response.isError());
                                JsonObject body = new JsonObject(response.firstContent().asText().text());
                                assertEquals("COMPLETED", body.getString("outcome"));
                                assertEquals(24, body.getInteger("matched"));
                            })
                    .thenAssertResults();
        }
    }

    @Test
    void clientWithElicitationRunsReconciliation() {
        long batchesBefore = ReconciliationBatch.count();

        try (ElicitationTestClient client = ElicitationTestClient.acceptDefaults()) {
            JsonObject body = client.reconcileAcmeMay();
            assertEquals("COMPLETED", body.getString("outcome"));
            assertEquals(32, body.getInteger("processed"));
            assertEquals(24, body.getInteger("matched"));
            assertEquals(8, body.getInteger("exceptions"));
            assertEquals("READY_FOR_REVIEW", body.getString("status"));
        }

        assertEquals(batchesBefore + 1, ReconciliationBatch.count());
        assertEquals(0, Invoice.count("posted", true));
    }

    @Test
    void postReconciliationBatchPostsMatchedInvoices() {
        String batchId;
        try (ElicitationTestClient client = ElicitationTestClient.acceptDefaults()) {
            batchId = client.reconcileAcmeMay().getString("batchId");
        }

        String result = postingService.postBatch(batchId);
        assertTrue(result.contains("Posted 24"));
        assertEquals(24, Invoice.count("posted", true));
    }
}
