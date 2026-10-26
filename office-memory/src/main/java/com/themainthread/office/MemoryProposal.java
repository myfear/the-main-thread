package com.themainthread.office;

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
@Table(name = "memory_proposal")
public class MemoryProposal {
    @Id
    public UUID id = UUID.randomUUID();
    @Column(name = "project_id", nullable = false)
    public UUID projectId;
    @Column(name = "fact_id", nullable = false)
    public UUID factId;
    @Column(name = "source_conversation_id", nullable = false)
    public UUID sourceConversationId;
    @Column(name = "source_message_id", nullable = false)
    public UUID sourceMessageId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public MemoryOperation operation;
    @Column(name = "target_memory_id")
    public UUID targetMemoryId;
    @Column(name = "target_version")
    public Long targetVersion;
    @Column(name = "proposed_text", length = 1000)
    public String proposedText;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public MemoryKind kind;
    @Column(length = 500)
    public String reason;
    @Column(nullable = false, length = 16)
    public String status = "PENDING";
    @Column(name = "dedup_key", nullable = false, length = 64)
    public String dedupKey;
    @Column(name = "result_memory_id")
    public UUID resultMemoryId;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
    @Column(name = "decided_at")
    public Instant decidedAt;
    @Version
    public long version;
}
