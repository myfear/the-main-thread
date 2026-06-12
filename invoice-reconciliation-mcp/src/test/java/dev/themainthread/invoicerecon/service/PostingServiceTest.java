package dev.themainthread.invoicerecon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.themainthread.invoicerecon.batch.ReconciliationBatch;
import dev.themainthread.invoicerecon.batch.ReconciliationBatchResult;
import dev.themainthread.invoicerecon.domain.BatchStatus;
import dev.themainthread.invoicerecon.domain.Invoice;
import dev.themainthread.invoicerecon.domain.InvoiceStatus;
import dev.themainthread.invoicerecon.domain.MissingGoodsReceiptAction;
import dev.themainthread.invoicerecon.policy.ReconciliationPolicy;
import dev.themainthread.invoicerecon.support.NoopProgress;
import dev.themainthread.invoicerecon.support.TestCancellation;
import dev.themainthread.invoicerecon.support.TestDataCleaner;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class PostingServiceTest {

    @Inject
    ReconciliationService reconciliationService;

    @Inject
    PostingService postingService;

    @Inject
    TestDataCleaner testDataCleaner;

    @BeforeEach
    void resetData() {
        testDataCleaner.resetReconciliationState();
    }

    @Test
    void reconcileDoesNotPostInvoices() {
        reconciliationService.runBatch(
                "ACME",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                new ReconciliationPolicy(2.5, MissingGoodsReceiptAction.FLAG_FOR_REVIEW, false, "FIN-OPERATIONS"),
                new NoopProgress(),
                new TestCancellation(Integer.MAX_VALUE));

        long posted = Invoice.count("posted", true);
        assertEquals(0, posted);
    }

    @Test
    void postingMarksMatchedInvoicesPosted() {
        ReconciliationBatchResult result = reconciliationService.runBatch(
                "ACME",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                new ReconciliationPolicy(2.5, MissingGoodsReceiptAction.FLAG_FOR_REVIEW, false, "FIN-OPERATIONS"),
                new NoopProgress(),
                new TestCancellation(Integer.MAX_VALUE));

        postingService.postBatch(result.batchId());

        ReconciliationBatch batch = ReconciliationBatch.findByBatchId(result.batchId());
        assertEquals(BatchStatus.POSTED, batch.status);
        assertEquals(24, Invoice.count("posted", true));

        Invoice matched = Invoice.find("invoiceNumber", "INV-001").firstResult();
        assertEquals(InvoiceStatus.POSTED, matched.status);
        assertTrue(matched.posted);

        Invoice exception = Invoice.find("invoiceNumber", "INV-025").firstResult();
        assertFalse(exception.posted);
    }

    @Test
    void doublePostIsIdempotent() {
        ReconciliationBatchResult result = reconciliationService.runBatch(
                "ACME",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                new ReconciliationPolicy(2.5, MissingGoodsReceiptAction.FLAG_FOR_REVIEW, false, "FIN-OPERATIONS"),
                new NoopProgress(),
                new TestCancellation(Integer.MAX_VALUE));

        postingService.postBatch(result.batchId());
        String second = postingService.postBatch(result.batchId());

        assertTrue(second.contains("already posted"));
    }
}
