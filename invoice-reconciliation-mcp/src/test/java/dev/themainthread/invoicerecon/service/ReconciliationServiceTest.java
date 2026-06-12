package dev.themainthread.invoicerecon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.themainthread.invoicerecon.batch.ReconciliationBatch;
import dev.themainthread.invoicerecon.batch.ReconciliationBatchResult;
import dev.themainthread.invoicerecon.domain.BatchStatus;
import dev.themainthread.invoicerecon.domain.Invoice;
import dev.themainthread.invoicerecon.domain.MissingGoodsReceiptAction;
import dev.themainthread.invoicerecon.domain.ReconciliationOutcome;
import dev.themainthread.invoicerecon.domain.ReconciliationStatus;
import dev.themainthread.invoicerecon.policy.ReconciliationPolicy;
import dev.themainthread.invoicerecon.support.NoopProgress;
import dev.themainthread.invoicerecon.support.TestCancellation;
import dev.themainthread.invoicerecon.support.TestDataCleaner;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class ReconciliationServiceTest {

    private static final LocalDate MAY_START = LocalDate.of(2026, 5, 1);
    private static final LocalDate MAY_END = LocalDate.of(2026, 5, 31);

    @Inject
    ReconciliationService reconciliationService;

    @Inject
    TestDataCleaner testDataCleaner;

    @BeforeEach
    void resetData() {
        testDataCleaner.resetReconciliationState();
    }

    @Test
    void matchedInvoicePassesChecks() {
        Invoice invoice = Invoice.find("invoiceNumber", "INV-001").firstResult();
        ReconciliationPolicy policy = defaultPolicy();

        ReconciliationStatus status = reconciliationService.reconcileInvoice(invoice, policy);

        assertEquals(ReconciliationStatus.MATCHED, status);
    }

    @Test
    void priceVarianceInvoiceIsFlagged() {
        Invoice invoice = Invoice.find("invoiceNumber", "INV-025").firstResult();
        ReconciliationPolicy policy = defaultPolicy();

        ReconciliationStatus status = reconciliationService.reconcileInvoice(invoice, policy);

        assertEquals(ReconciliationStatus.PRICE_VARIANCE, status);
    }

    @Test
    void missingGoodsReceiptInvoiceIsFlagged() {
        Invoice invoice = Invoice.find("invoiceNumber", "INV-029").firstResult();
        ReconciliationPolicy policy = defaultPolicy();

        ReconciliationStatus status = reconciliationService.reconcileInvoice(invoice, policy);

        assertEquals(ReconciliationStatus.MISSING_GOODS_RECEIPT, status);
    }

    @Test
    void missingPurchaseOrderInvoiceIsFlagged() {
        Invoice invoice = Invoice.find("invoiceNumber", "INV-031").firstResult();
        ReconciliationPolicy policy = defaultPolicy();

        ReconciliationStatus status = reconciliationService.reconcileInvoice(invoice, policy);

        assertEquals(ReconciliationStatus.MISSING_PURCHASE_ORDER, status);
    }

    @Test
    void batchReconciliationProducesExpectedCounts() {
        ReconciliationPolicy policy = defaultPolicy();

        ReconciliationBatchResult result = reconciliationService.runBatch(
                "ACME",
                MAY_START,
                MAY_END,
                policy,
                new NoopProgress(),
                new TestCancellation(Integer.MAX_VALUE));

        assertEquals(ReconciliationOutcome.COMPLETED, result.outcome());
        assertEquals(32, result.processed());
        assertEquals(24, result.matched());
        assertEquals(8, result.exceptions());
        assertEquals(BatchStatus.READY_FOR_REVIEW, result.status());
        assertNotNull(result.batchId());
    }

    @Test
    void cancellationMarksBatchCancelled() {
        ReconciliationPolicy policy = defaultPolicy();

        ReconciliationBatchResult result = reconciliationService.runBatch(
                "ACME",
                MAY_START,
                MAY_END,
                policy,
                new NoopProgress(),
                new TestCancellation(12));

        assertEquals(ReconciliationOutcome.CANCELLED, result.outcome());
        assertEquals(BatchStatus.CANCELLED, result.status());
        assertEquals(12, result.processed());
    }

    @Test
    void duplicateBatchReturnsExistingResult() {
        ReconciliationPolicy policy = defaultPolicy();
        TestCancellation neverCancel = new TestCancellation(Integer.MAX_VALUE);

        ReconciliationBatchResult first = reconciliationService.runBatch(
                "ACME", MAY_START, MAY_END, policy, new NoopProgress(), neverCancel);
        ReconciliationBatchResult second = reconciliationService.runBatch(
                "ACME", MAY_START, MAY_END, policy, new NoopProgress(), neverCancel);

        assertEquals(first.batchId(), second.batchId());
        assertEquals(1, ReconciliationBatch.count());
    }

    private ReconciliationPolicy defaultPolicy() {
        return new ReconciliationPolicy(2.5, MissingGoodsReceiptAction.FLAG_FOR_REVIEW, false, "FIN-OPERATIONS");
    }
}
