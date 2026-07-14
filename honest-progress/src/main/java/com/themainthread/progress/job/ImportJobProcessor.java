package com.themainthread.progress.job;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.themainthread.progress.config.ProgressConfig;
import com.themainthread.progress.domain.InvoiceRow;
import com.themainthread.progress.persistence.JobStore;
import com.themainthread.progress.storage.FileStorage;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

@ApplicationScoped
public class ImportJobProcessor {

    private static final Logger LOG = Logger.getLogger(ImportJobProcessor.class);

    private final JobStore store;
    private final CsvInvoiceReader csvReader;
    private final FileStorage fileStorage;
    private final ProgressConfig config;

    public ImportJobProcessor(JobStore store, CsvInvoiceReader csvReader, FileStorage fileStorage, ProgressConfig config) {
        this.store = store;
        this.csvReader = csvReader;
        this.fileStorage = fileStorage;
        this.config = config;
    }

    public void process(UUID id) {
        Path path = store.storedPath(id);
        try {
            if (cancelIfRequested(id)) {
                return;
            }

            long totalUnits = csvReader.validateAndCount(path);
            store.beginImport(id, totalUnits);
            importRows(id, path);

            if (cancelIfRequested(id)) {
                return;
            }

            store.markFinalizing(id);
            pause();
            if (cancelIfRequested(id)) {
                return;
            }
            store.complete(id);
        } catch (CancellationDetectedException e) {
            store.cancelRunning(id);
        } catch (Exception e) {
            LOG.errorf(e, "Import job %s failed", id);
            store.fail(id, failureMessage(e));
        } finally {
            fileStorage.deleteQuietly(path);
        }
    }

    private void importRows(UUID id, Path path) throws IOException {
        List<InvoiceRow> batch = new ArrayList<>(config.batchSize());
        csvReader.read(path, row -> {
            batch.add(row);
            if (batch.size() == config.batchSize()) {
                persistBatch(id, batch);
            }
        });
        if (!batch.isEmpty()) {
            persistBatch(id, batch);
        }
    }

    private void persistBatch(UUID id, List<InvoiceRow> batch) {
        if (store.cancellationRequested(id)) {
            throw new CancellationDetectedException();
        }
        pause();
        store.persistBatch(id, List.copyOf(batch));
        batch.clear();
    }

    private boolean cancelIfRequested(UUID id) {
        if (store.cancellationRequested(id)) {
            store.cancelRunning(id);
            return true;
        }
        return false;
    }

    private void pause() {
        try {
            Thread.sleep(config.processingDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Processing thread was interrupted", e);
        }
    }

    private String failureMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static final class CancellationDetectedException extends RuntimeException {
    }
}
