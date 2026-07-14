package com.themainthread.progress.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.themainthread.progress.domain.ImportJob;
import com.themainthread.progress.domain.ImportProgress;
import com.themainthread.progress.domain.InvoiceRecord;
import com.themainthread.progress.domain.InvoiceRow;
import com.themainthread.progress.domain.JobPhase;
import com.themainthread.progress.domain.JobState;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class JobStore {

    private final ImportJobRepository jobs;
    private final InvoiceRecordRepository invoices;

    public JobStore(ImportJobRepository jobs, InvoiceRecordRepository invoices) {
        this.jobs = jobs;
        this.invoices = invoices;
    }

    @Transactional
    public ImportProgress create(UUID id, String originalFileName, Path storedPath, long fileSize) {
        Instant now = Instant.now();
        ImportJob job = new ImportJob();
        job.id = id;
        job.originalFileName = originalFileName;
        job.storedPath = storedPath.toString();
        job.fileSize = fileSize;
        job.jobState = JobState.QUEUED;
        job.jobPhase = JobPhase.QUEUED;
        job.message = "Waiting for a worker";
        job.createdAt = now;
        job.updatedAt = now;
        jobs.persist(job);
        jobs.flush();
        return ImportProgress.from(job);
    }

    @Transactional
    public ImportProgress snapshot(UUID id) {
        return ImportProgress.from(required(id));
    }

    @Transactional
    public Path storedPath(UUID id) {
        return Path.of(required(id).storedPath);
    }

    @Transactional
    public Optional<UUID> claimNext() {
        ImportJob job = jobs.find("jobState = ?1 order by createdAt", JobState.QUEUED)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        if (job == null) {
            return Optional.empty();
        }
        job.jobState = JobState.RUNNING;
        job.jobPhase = JobPhase.VALIDATING;
        job.message = "Checking the CSV before importing any rows";
        job.updatedAt = Instant.now();
        return Optional.of(job.id);
    }

    @Transactional
    public boolean cancellationRequested(UUID id) {
        return required(id).cancellationRequested;
    }

    @Transactional
    public void beginImport(UUID id, long totalUnits) {
        ImportJob job = required(id);
        job.jobPhase = JobPhase.IMPORTING;
        job.totalUnits = totalUnits;
        job.completedUnits = 0;
        job.message = "Importing 0 of " + totalUnits + " invoices";
        job.updatedAt = Instant.now();
    }

    @Transactional
    public void persistBatch(UUID id, List<InvoiceRow> rows) {
        ImportJob job = required(id);
        for (InvoiceRow row : rows) {
            InvoiceRecord invoice = new InvoiceRecord();
            invoice.jobId = id;
            invoice.invoiceNumber = row.invoiceNumber();
            invoice.amount = row.amount();
            invoice.currency = row.currency();
            invoice.published = false;
            invoices.persist(invoice);
        }
        job.completedUnits += rows.size();
        job.message = "Imported " + job.completedUnits + " of " + job.totalUnits + " invoices";
        job.updatedAt = Instant.now();
        invoices.flush();
    }

    @Transactional
    public void markFinalizing(UUID id) {
        ImportJob job = required(id);
        job.jobPhase = JobPhase.FINALIZING;
        job.message = "Publishing the validated batch";
        job.updatedAt = Instant.now();
    }

    @Transactional
    public void complete(UUID id) {
        ImportJob job = required(id);
        long published = invoices.update("published = true where jobId = ?1", id);
        job.jobState = JobState.SUCCEEDED;
        job.jobPhase = JobPhase.COMPLETE;
        job.publishedCount = published;
        job.message = "Published " + published + " invoices";
        job.updatedAt = Instant.now();
        job.finishedAt = job.updatedAt;
    }

    @Transactional
    public CancelDecision requestCancellation(UUID id) {
        ImportJob job = required(id);
        if (job.jobState.terminal()) {
            return new CancelDecision(ImportProgress.from(job), null);
        }

        job.cancellationRequested = true;
        job.updatedAt = Instant.now();
        if (job.jobState == JobState.QUEUED) {
            job.jobState = JobState.CANCELLED;
            job.jobPhase = JobPhase.COMPLETE;
            job.message = "Cancelled before processing started";
            job.finishedAt = job.updatedAt;
            return new CancelDecision(ImportProgress.from(job), Path.of(job.storedPath));
        }

        job.message = "Cancellation requested; finishing the current batch";
        return new CancelDecision(ImportProgress.from(job), null);
    }

    @Transactional
    public void cancelRunning(UUID id) {
        invoices.delete("jobId", id);
        ImportJob job = required(id);
        job.jobState = JobState.CANCELLED;
        job.jobPhase = JobPhase.COMPLETE;
        job.message = "Cancelled after " + job.completedUnits + " invoices";
        job.updatedAt = Instant.now();
        job.finishedAt = job.updatedAt;
    }

    @Transactional
    public void fail(UUID id, String message) {
        invoices.delete("jobId", id);
        ImportJob job = required(id);
        job.jobState = JobState.FAILED;
        job.jobPhase = JobPhase.COMPLETE;
        job.message = "Import failed";
        job.errorMessage = message;
        job.updatedAt = Instant.now();
        job.finishedAt = job.updatedAt;
    }

    @Transactional
    public List<RecoveryAction> recoverInterrupted() {
        List<ImportJob> interrupted = jobs.list("jobState", JobState.RUNNING);
        List<RecoveryAction> actions = new ArrayList<>();
        for (ImportJob job : interrupted) {
            invoices.delete("jobId", job.id);
            job.completedUnits = 0;
            job.totalUnits = 0;
            job.updatedAt = Instant.now();
            if (job.cancellationRequested) {
                job.jobState = JobState.CANCELLED;
                job.jobPhase = JobPhase.COMPLETE;
                job.message = "Cancelled during application restart";
                job.finishedAt = job.updatedAt;
                actions.add(new RecoveryAction(Path.of(job.storedPath), true));
            } else {
                job.jobState = JobState.QUEUED;
                job.jobPhase = JobPhase.QUEUED;
                job.message = "Recovered after application restart";
                actions.add(new RecoveryAction(Path.of(job.storedPath), false));
            }
        }
        return actions;
    }

    @Transactional
    public long publishedCount(UUID id) {
        return invoices.count("jobId = ?1 and published = true", id);
    }

    private ImportJob required(UUID id) {
        ImportJob job = jobs.findById(id);
        if (job == null) {
            throw new NotFoundException("Import job " + id + " was not found");
        }
        return job;
    }

    public record CancelDecision(ImportProgress progress, Path pathToDelete) {
    }

    public record RecoveryAction(Path storedPath, boolean deleteFile) {
    }
}
