package com.themainthread.office;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "conversation")
public class Conversation {
    @Id
    public UUID id = UUID.randomUUID();
    @Column(name = "project_id", nullable = false)
    public UUID projectId;
    @Column(nullable = false, length = 120)
    public String title;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();
    @Version
    public long version;
}
