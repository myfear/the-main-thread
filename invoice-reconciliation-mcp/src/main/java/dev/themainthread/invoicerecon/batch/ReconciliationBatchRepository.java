package dev.themainthread.invoicerecon.batch;

import java.time.LocalDate;
import java.util.List;

import dev.themainthread.invoicerecon.domain.BatchStatus;
import dev.themainthread.invoicerecon.domain.ReconciliationStatus;
import dev.themainthread.invoicerecon.policy.ReconciliationPolicy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ReconciliationBatchRepository {

    public ReconciliationBatch createBatch(
            String batchId,
            String idempotencyKey,
            String supplierId,
            LocalDate from,
            LocalDate to,
            BatchStatus status) {
        ReconciliationBatch batch = new ReconciliationBatch();
        batch.batchId = batchId;
        batch.idempotencyKey = idempotencyKey;
        batch.supplierId = supplierId;
        batch.fromDate = from;
        batch.toDate = to;
        batch.status = status;
        batch.processed = 0;
        batch.matched = 0;
        batch.exceptions = 0;
        batch.persist();
        return batch;
    }

    public void addLine(ReconciliationBatch batch, Long invoiceId, String invoiceNumber, ReconciliationStatus status) {
        ReconciliationLine line = new ReconciliationLine();
        line.batch = batch;
        line.invoiceId = invoiceId;
        line.invoiceNumber = invoiceNumber;
        line.status = status;
        line.persist();

        batch.processed++;
        if (status == ReconciliationStatus.MATCHED) {
            batch.matched++;
        } else if (status != ReconciliationStatus.CANCELLED) {
            batch.exceptions++;
        }
    }

    public void markCancelled(ReconciliationBatch batch) {
        batch.status = BatchStatus.CANCELLED;
    }

    public void markPosted(ReconciliationBatch batch) {
        batch.status = BatchStatus.POSTED;
    }

    public ReconciliationBatch findByIdempotencyKey(String key) {
        return ReconciliationBatch.findByIdempotencyKey(key);
    }

    public ReconciliationBatch findByBatchId(String batchId) {
        return ReconciliationBatch.findByBatchId(batchId);
    }

    public List<ReconciliationLine> linesForBatch(ReconciliationBatch batch) {
        return ReconciliationLine.list("batch", batch);
    }

    public static String buildIdempotencyKey(
            String supplierId,
            LocalDate from,
            LocalDate to,
            ReconciliationPolicy policy) {
        return supplierId + "|" + from + "|" + to + "|" + policy.idempotencyFragment();
    }
}
