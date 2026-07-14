package com.themainthread.progress.job;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.themainthread.progress.config.ProgressConfig;
import com.themainthread.progress.domain.ImportProgress;
import com.themainthread.progress.persistence.JobStore;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProgressStreamService {

    private final JobStore store;
    private final ProgressConfig config;

    public ProgressStreamService(JobStore store, ProgressConfig config) {
        this.store = store;
        this.config = config;
    }

    public Multi<ImportProgress> stream(UUID id, ImportProgress initial) {
        AtomicBoolean terminalSeen = new AtomicBoolean();
        AtomicLong lastVersion = new AtomicLong(Long.MIN_VALUE);
        Multi<ImportProgress> changes = Multi.createFrom().ticks().every(config.streamInterval())
                .onItem().transformToUniAndConcatenate(ignored -> snapshotAsync(id));

        return Multi.createBy().concatenating()
                .streams(Multi.createFrom().item(initial), changes)
                .select().first(progress -> !terminalSeen.getAndSet(progress.terminal()))
                .select().where(progress -> progress.version() != lastVersion.getAndSet(progress.version()));
    }

    private Uni<ImportProgress> snapshotAsync(UUID id) {
        return Uni.createFrom().item(() -> store.snapshot(id))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
