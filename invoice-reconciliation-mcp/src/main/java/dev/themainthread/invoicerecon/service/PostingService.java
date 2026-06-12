package dev.themainthread.invoicerecon.service;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import dev.themainthread.invoicerecon.batch.ReconciliationBatch;
import dev.themainthread.invoicerecon.batch.ReconciliationBatchRepository;
import dev.themainthread.invoicerecon.batch.ReconciliationLine;
import dev.themainthread.invoicerecon.domain.BatchStatus;
import dev.themainthread.invoicerecon.domain.Invoice;
import dev.themainthread.invoicerecon.domain.InvoiceStatus;
import dev.themainthread.invoicerecon.domain.ReconciliationStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PostingService {

    private static final Logger LOG = Logger.getLogger(PostingService.class);

    @Inject
    ReconciliationBatchRepository batchRepository;

    @ConfigProperty(name = "invoice-reconciliation.posting.enabled", defaultValue = "true")
    boolean postingEnabled;

    @Transactional
    public String postBatch(String batchId) {
        if (!postingEnabled) {
            throw new IllegalStateException("Posting is disabled by configuration");
        }

        ReconciliationBatch batch = batchRepository.findByBatchId(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("Unknown reconciliation batch: " + batchId);
        }

        if (batch.status == BatchStatus.POSTED) {
            return "Batch " + batchId + " was already posted";
        }

        if (batch.status != BatchStatus.READY_FOR_REVIEW) {
            throw new IllegalStateException("Batch " + batchId + " is not ready for posting (status=" + batch.status + ")");
        }

        int postedCount = 0;
        for (ReconciliationLine line : batchRepository.linesForBatch(batch)) {
            if (line.status != ReconciliationStatus.MATCHED) {
                continue;
            }
            Invoice invoice = Invoice.findById(line.invoiceId);
            if (invoice == null) {
                continue;
            }
            invoice.status = InvoiceStatus.POSTED;
            invoice.posted = true;
            postedCount++;
        }

        batchRepository.markPosted(batch);
        LOG.infov("Posted {0} matched invoices from batch {1}", postedCount, batchId);
        return "Posted " + postedCount + " matched invoices from batch " + batchId;
    }
}
