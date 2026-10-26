package com.themainthread.office;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversation_message")
public class ConversationMessage {
    @Id
    public UUID id = UUID.randomUUID();
    @Column(name = "conversation_id", nullable = false)
    public UUID conversationId;
    @Column(nullable = false, length = 12)
    public String role;
    @Column(nullable = false, columnDefinition = "text")
    public String content;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
    @Column(nullable = false)
    public long sequence;
    @Column(name = "request_id")
    public UUID requestId;
    @Column(name = "reply_to_id")
    public UUID replyToId;
    @Column(nullable = false, length = 16)
    public String state = "COMPLETE";
}
