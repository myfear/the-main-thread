package dev.themainthread.invoicerecon.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;

import dev.themainthread.invoicerecon.batch.ReconciliationBatch;
import dev.themainthread.invoicerecon.batch.ReconciliationBatchRepository;
import dev.themainthread.invoicerecon.batch.ReconciliationBatchResult;
import dev.themainthread.invoicerecon.domain.BatchStatus;
import dev.themainthread.invoicerecon.domain.GoodsReceipt;
import dev.themainthread.invoicerecon.domain.Invoice;
import dev.themainthread.invoicerecon.domain.MissingGoodsReceiptAction;
import dev.themainthread.invoicerecon.domain.PurchaseOrder;
import dev.themainthread.invoicerecon.domain.ReconciliationOutcome;
import dev.themainthread.invoicerecon.domain.ReconciliationStatus;
import dev.themainthread.invoicerecon.policy.ReconciliationPolicy;
import dev.themainthread.invoicerecon.policy.SupplierIdResolver;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Cancellation.OperationCancellationException;
import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.ProgressTracker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ReconciliationService {

    private static final Logger LOG = Logger.getLogger(ReconciliationService.class);
    private static final int PROGRESS_INTERVAL = 8;

    @Inject
    ReconciliationBatchRepository batchRepository;

    @Inject
    BatchIdGenerator batchIdGenerator;

    @Inject
    SupplierIdResolver supplierIdResolver;

    public ReconciliationStatus reconcileInvoice(Invoice invoice, ReconciliationPolicy policy) {
        if (invoice.purchaseOrderNumber == null || invoice.purchaseOrderNumber.isBlank()) {
            return ReconciliationStatus.MISSING_PURCHASE_ORDER;
        }

        PurchaseOrder purchaseOrder = PurchaseOrder.findByPoNumber(invoice.purchaseOrderNumber);
        if (purchaseOrder == null) {
            return ReconciliationStatus.MISSING_PURCHASE_ORDER;
        }

        GoodsReceipt goodsReceipt = GoodsReceipt.findByPoNumber(invoice.purchaseOrderNumber);
        if (goodsReceipt == null) {
            if (policy.missingGoodsReceiptAction() == MissingGoodsReceiptAction.REJECT_INVOICE) {
                return ReconciliationStatus.MISSING_GOODS_RECEIPT;
            }
            return ReconciliationStatus.MISSING_GOODS_RECEIPT;
        }

        if (invoice.quantity != purchaseOrder.quantity) {
            return ReconciliationStatus.QUANTITY_VARIANCE;
        }

        BigDecimal variancePercent = variancePercent(invoice.unitPrice, purchaseOrder.unitPrice);
        if (variancePercent.doubleValue() > policy.maximumVariancePercent()) {
            return ReconciliationStatus.PRICE_VARIANCE;
        }

        if (invoice.costCenter == null || invoice.costCenter.isBlank()) {
            invoice.costCenter = policy.defaultCostCenter();
        }

        return ReconciliationStatus.MATCHED;
    }

    @Transactional
    public ReconciliationBatchResult runBatch(
            String supplierId,
            LocalDate from,
            LocalDate to,
            ReconciliationPolicy policy,
            Progress progress,
            Cancellation cancellation) {
        String resolvedSupplierId = supplierIdResolver.resolve(supplierId);
        String idempotencyKey =
                ReconciliationBatchRepository.buildIdempotencyKey(resolvedSupplierId, from, to, policy);
        ReconciliationBatch existing = batchRepository.findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return ReconciliationBatchResult.fromBatch(existing, ReconciliationOutcome.COMPLETED);
        }

        List<Invoice> invoices = Invoice.findOpenForSupplierAndPeriod(resolvedSupplierId, from, to);
        if (invoices.isEmpty()) {
            return ReconciliationBatchResult.message(
                    ReconciliationOutcome.NO_INVOICES_FOUND,
                    "No open invoices matched supplier "
                            + supplierId
                            + " between "
                            + from
                            + " and "
                            + to
                            + ". "
                            + supplierIdResolver.knownSupplierIdsMessage());
        }

        ReconciliationBatch batch = batchRepository.createBatch(
                batchIdGenerator.nextBatchId(),
                idempotencyKey,
                resolvedSupplierId,
                from,
                to,
                BatchStatus.READY_FOR_REVIEW);

        ProgressTracker invoiceTracker = buildInvoiceTracker(progress, invoices.size());
        AtomicInteger phase = new AtomicInteger(1);

        try {
            reportPhase(progress, phase.get(), "Loading open invoices");
            reportPhase(progress, phase.incrementAndGet(), "Matching purchase orders");

            int index = 0;
            for (Invoice invoice : invoices) {
                cancellation.skipProcessingIfCancelled();

                ReconciliationStatus status = reconcileInvoice(invoice, policy);
                batchRepository.addLine(batch, invoice.id, invoice.invoiceNumber, status);

                index++;
                if (invoiceTracker != null && index % PROGRESS_INTERVAL == 0) {
                    invoiceTracker.advanceAndForget();
                }
            }

            reportPhase(progress, phase.incrementAndGet(), "Checking goods receipts");
            reportPhase(progress, phase.incrementAndGet(), "Applying variance rules");
            reportPhase(progress, phase.incrementAndGet(), "Generating reconciliation report");

            LOG.infov(
                    "Reconciliation batch {0} completed: processed={1}, matched={2}, exceptions={3}",
                    batch.batchId,
                    batch.processed,
                    batch.matched,
                    batch.exceptions);

            return ReconciliationBatchResult.fromBatch(batch, ReconciliationOutcome.COMPLETED);
        } catch (OperationCancellationException ex) {
            batchRepository.markCancelled(batch);
            LOG.infov("Reconciliation batch {0} cancelled after {1} invoices", batch.batchId, batch.processed);
            return ReconciliationBatchResult.fromBatch(batch, ReconciliationOutcome.CANCELLED);
        } catch (RuntimeException ex) {
            LOG.errorv(ex, "Reconciliation batch {0} failed", batch.batchId);
            batchRepository.markCancelled(batch);
            return ReconciliationBatchResult.message(
                    ReconciliationOutcome.FAILED,
                    "Reconciliation failed: " + ex.getMessage());
        }
    }

    private ProgressTracker buildInvoiceTracker(Progress progress, int total) {
        if (progress == null || progress.token().isEmpty()) {
            return null;
        }
        return progress.trackerBuilder()
                .setTotal(total)
                .setDefaultStep(PROGRESS_INTERVAL)
                .setMessageBuilder(current -> "Reconciled " + current + " of " + total + " invoices")
                .build();
    }

    private void reportPhase(Progress progress, int phaseNumber, String label) {
        if (progress == null || progress.token().isEmpty()) {
            return;
        }
        progress.notificationBuilder()
                .setProgress(phaseNumber)
                .setTotal(5)
                .setMessage("Phase " + phaseNumber + " of 5: " + label)
                .build()
                .sendAndForget();
    }

    private BigDecimal variancePercent(BigDecimal invoicePrice, BigDecimal poPrice) {
        if (poPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return invoicePrice.subtract(poPrice)
                .abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(poPrice, 4, RoundingMode.HALF_UP);
    }
}
