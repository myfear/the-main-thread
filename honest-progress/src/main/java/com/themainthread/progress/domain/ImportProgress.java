package com.themainthread.progress.domain;

import java.time.Instant;
import java.util.UUID;

public record ImportProgress(
        UUID id,
        String fileName,
        long fileSize,
        JobState state,
        JobPhase phase,
        long completedUnits,
        long totalUnits,
        Integer percent,
        long publishedCount,
        boolean cancellationRequested,
        String message,
        String error,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt,
        long version) {

    public boolean terminal() {
        return state.terminal();
    }

    public static ImportProgress from(ImportJob job) {
        return new ImportProgress(
                job.id,
                job.originalFileName,
                job.fileSize,
                job.jobState,
                job.jobPhase,
                job.completedUnits,
                job.totalUnits,
                percentage(job),
                job.publishedCount,
                job.cancellationRequested,
                job.message,
                job.errorMessage,
                job.createdAt,
                job.updatedAt,
                job.finishedAt,
                job.version);
    }

    private static Integer percentage(ImportJob job) {
        if (job.jobState == JobState.SUCCEEDED) {
            return 100;
        }
        if (job.totalUnits == 0 || job.jobPhase == JobPhase.VALIDATING || job.jobPhase == JobPhase.FINALIZING) {
            return null;
        }
        return (int) Math.min(100, job.completedUnits * 100 / job.totalUnits);
    }
}
