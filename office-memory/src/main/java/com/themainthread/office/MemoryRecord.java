package com.themainthread.office;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import dev.langchain4j.store.embedding.hibernate.EmbeddedText;
import dev.langchain4j.store.embedding.hibernate.EmbeddingVector;
import dev.langchain4j.store.embedding.hibernate.MetadataAttribute;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;

@Entity
@Table(name = "memory_record")
public class MemoryRecord {
    @Id
    public UUID id = UUID.randomUUID();
    @Column(name = "fact_id", nullable = false)
    public UUID factId;
    @MetadataAttribute
    @Column(name = "project_id", nullable = false)
    public UUID projectId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public MemoryKind kind;
    @EmbeddedText
    @Column(nullable = false, columnDefinition = "text")
    public String text;
    @EmbeddingVector
    @Array(length = 768)
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(nullable = false, columnDefinition = "vector(768)")
    public float[] embedding;
    @UnmappedMetadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    public Map<String, Object> metadata = new HashMap<>();
    @MetadataAttribute
    @Column(nullable = false, length = 16)
    public String status = "ACTIVE";
    @Column(name = "source_conversation_id", nullable = false)
    public UUID sourceConversationId;
    @Column(name = "source_message_id", nullable = false)
    public UUID sourceMessageId;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
    @Column(name = "superseded_at")
    public Instant supersededAt;
    @Column(name = "superseded_by_id")
    public UUID supersededById;
    @Version
    public long version;
}
