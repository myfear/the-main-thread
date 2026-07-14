package com.themainthread.progress.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "import_job")
public class ImportJob {

    @Id
    public UUID id;

    @Column(name = "original_file_name", nullable = false, length = 255)
    public String originalFileName;

    @Column(name = "stored_path", nullable = false, length = 1024)
    public String storedPath;

    @Column(name = "file_size", nullable = false)
    public long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_state", nullable = false, length = 32)
    public JobState jobState;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_phase", nullable = false, length = 32)
    public JobPhase jobPhase;

    @Column(name = "completed_units", nullable = false)
    public long completedUnits;

    @Column(name = "total_units", nullable = false)
    public long totalUnits;

    @Column(name = "published_count", nullable = false)
    public long publishedCount;

    @Column(name = "cancellation_requested", nullable = false)
    public boolean cancellationRequested;

    @Column(nullable = false, length = 500)
    public String message;

    @Column(name = "error_message", length = 2000)
    public String errorMessage;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "finished_at")
    public Instant finishedAt;

    @Version
    public long version;
}
