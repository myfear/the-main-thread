package com.themainthread.progress.api;

import java.util.UUID;

import com.themainthread.progress.domain.ImportProgress;
import com.themainthread.progress.persistence.JobStore;
import com.themainthread.progress.persistence.JobStore.CancelDecision;
import com.themainthread.progress.storage.FileStorage;
import com.themainthread.progress.storage.FileStorage.StagedFile;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.resteasy.reactive.multipart.FileUpload;

@ApplicationScoped
public class ImportJobService {

    private final JobStore store;
    private final FileStorage fileStorage;

    public ImportJobService(JobStore store, FileStorage fileStorage) {
        this.store = store;
        this.fileStorage = fileStorage;
    }

    public ImportProgress create(FileUpload upload) {
        UUID id = UUID.randomUUID();
        StagedFile stagedFile = fileStorage.stage(upload, id);
        try {
            return store.create(id, stagedFile.originalName(), stagedFile.path(), stagedFile.size());
        } catch (RuntimeException e) {
            fileStorage.deleteQuietly(stagedFile.path());
            throw e;
        }
    }

    public ImportProgress snapshot(UUID id) {
        return store.snapshot(id);
    }

    public ImportProgress cancel(UUID id) {
        CancelDecision decision = store.requestCancellation(id);
        if (decision.pathToDelete() != null) {
            fileStorage.deleteQuietly(decision.pathToDelete());
        }
        return decision.progress();
    }
}
