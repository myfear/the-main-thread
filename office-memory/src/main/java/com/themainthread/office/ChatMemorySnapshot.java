package com.themainthread.office;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "chat_memory_snapshot")
public class ChatMemorySnapshot {
    @Id
    @Column(name = "memory_id")
    public UUID memoryId;
    @Column(name = "messages_json", nullable = false, columnDefinition = "text")
    public String messagesJson;
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();
    @Version
    public long version;
}
