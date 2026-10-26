package com.themainthread.office;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.BadRequestException;

@RequestScoped
public class InvocationContext {
    private UUID conversationId;
    private UUID projectId;
    private UUID messageId;
    private int calls;
    private final Set<UUID> retrieved = new HashSet<>();

    public void begin(UUID conversationId, UUID projectId, UUID messageId) {
        this.conversationId = conversationId;
        this.projectId = projectId;
        this.messageId = messageId;
        calls = 0;
        retrieved.clear();
    }

    public void verify(Object memoryId) {
        if (conversationId == null || !conversationId.toString().equals(String.valueOf(memoryId))) {
            throw new BadRequestException("Tool invocation has no matching conversation context.");
        }
        if (++calls > 20) {
            throw new BadRequestException("Tool call limit reached for this turn.");
        }
    }

    public UUID conversationId() {
        return conversationId;
    }

    public void recordSearch(ApiViews.SearchResult result) {
        result.matches().forEach(match -> retrieved.add(match.id()));
    }

    public void requireRetrieved(UUID target) {
        if (!retrieved.contains(target)) {
            throw new BadRequestException(
                    "Call memory_search in this turn and use an ID from its results before replacing or removing a fact.");
        }
    }

    public UUID projectId() {
        return projectId;
    }

    public UUID messageId() {
        return messageId;
    }
}
