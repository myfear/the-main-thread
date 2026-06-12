package dev.themainthread.invoicerecon.batch;

import dev.themainthread.invoicerecon.domain.BatchStatus;
import dev.themainthread.invoicerecon.domain.ReconciliationOutcome;

public record ReconciliationBatchResult(
        ReconciliationOutcome outcome,
        String batchId,
        String supplier,
        int processed,
        int matched,
        int exceptions,
        BatchStatus status,
        String message) {

    public static ReconciliationBatchResult fromBatch(ReconciliationBatch batch, ReconciliationOutcome outcome) {
        return new ReconciliationBatchResult(
                outcome,
                batch.batchId,
                batch.supplierId,
                batch.processed,
                batch.matched,
                batch.exceptions,
                batch.status,
                null);
    }

    public static ReconciliationBatchResult message(ReconciliationOutcome outcome, String message) {
        return new ReconciliationBatchResult(outcome, null, null, 0, 0, 0, null, message);
    }
}
