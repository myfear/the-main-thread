package dev.themainthread.invoicerecon.support;

import dev.themainthread.invoicerecon.batch.ReconciliationBatch;
import dev.themainthread.invoicerecon.batch.ReconciliationLine;
import dev.themainthread.invoicerecon.domain.Invoice;
import dev.themainthread.invoicerecon.domain.InvoiceStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TestDataCleaner {

    @Transactional
    public void resetReconciliationState() {
        ReconciliationLine.deleteAll();
        ReconciliationBatch.deleteAll();
        Invoice.update("posted = false, status = ?1", InvoiceStatus.OPEN);
    }
}
