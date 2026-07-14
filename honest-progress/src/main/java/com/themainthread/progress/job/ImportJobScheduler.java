package com.themainthread.progress.job;

import java.util.List;

import com.themainthread.progress.persistence.JobStore;
import com.themainthread.progress.persistence.JobStore.RecoveryAction;
import com.themainthread.progress.storage.FileStorage;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class ImportJobScheduler {

    private final JobStore store;
    private final ImportJobProcessor processor;
    private final FileStorage fileStorage;

    public ImportJobScheduler(JobStore store, ImportJobProcessor processor, FileStorage fileStorage) {
        this.store = store;
        this.processor = processor;
        this.fileStorage = fileStorage;
    }

    void recover(@Observes StartupEvent event) {
        List<RecoveryAction> actions = store.recoverInterrupted();
        for (RecoveryAction action : actions) {
            if (action.deleteFile()) {
                fileStorage.deleteQuietly(action.storedPath());
            }
        }
    }

    @Scheduled(identity = "invoice-import-runner", every = "${progress.scheduler-interval}",
            concurrentExecution = ConcurrentExecution.SKIP)
    public void runNext() {
        store.claimNext().ifPresent(processor::process);
    }
}
