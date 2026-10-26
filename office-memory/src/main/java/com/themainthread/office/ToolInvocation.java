package com.themainthread.office;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tool_invocation")
public class ToolInvocation {
    @Id
    public UUID id = UUID.randomUUID();
    @Column(name = "conversation_id", nullable = false)
    public UUID conversationId;
    @Column(name = "source_message_id", nullable = false)
    public UUID sourceMessageId;
    @Column(name = "tool_name", nullable = false, length = 48)
    public String toolName;
    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt = Instant.now();
    @Column(name = "duration_ms", nullable = false)
    public long durationMs;
    @Column(nullable = false, length = 24)
    public String outcome;
    @Column(nullable = false, length = 120)
    public String description;
}
