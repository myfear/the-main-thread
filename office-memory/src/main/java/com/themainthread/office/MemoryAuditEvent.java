package com.themainthread.office;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "memory_audit_event")
public class MemoryAuditEvent {
    @Id
    public UUID id = UUID.randomUUID();
    @Column(name = "project_id", nullable = false)
    public UUID projectId;
    @Column(name = "proposal_id", nullable = false)
    public UUID proposalId;
    @Column(name = "memory_id")
    public UUID memoryId;
    @Column(nullable = false, length = 24)
    public String action;
    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt = Instant.now();
    @Column(nullable = false, length = 16)
    public String actor = "LOCAL_USER";
}
