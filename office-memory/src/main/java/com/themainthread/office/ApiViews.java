package com.themainthread.office;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiViews {
    private ApiViews() {
    }

    public record Fact(UUID id, UUID factId, MemoryKind kind, String text, String status,
            UUID sourceConversationId, UUID sourceMessageId, Instant createdAt, UUID supersededById, long version) {
        static Fact from(MemoryRecord m) {
            return new Fact(m.id, m.factId, m.kind, m.text, m.status, m.sourceConversationId,
                    m.sourceMessageId, m.createdAt, m.supersededById, m.version);
        }
    }

    public record Proposal(UUID id, MemoryOperation operation, UUID targetMemoryId, String proposedText,
            MemoryKind kind, String reason, String status, UUID sourceConversationId, UUID sourceMessageId,
            UUID resultMemoryId, Instant createdAt, long version) {
        static Proposal from(MemoryProposal p) {
            return new Proposal(p.id, p.operation, p.targetMemoryId, p.proposedText, p.kind, p.reason, p.status,
                    p.sourceConversationId, p.sourceMessageId, p.resultMemoryId, p.createdAt, p.version);
        }
    }

    public record Match(UUID id, MemoryKind kind, String text, UUID sourceConversationId,
            UUID sourceMessageId, double similarity) {
    }

    public record SearchResult(List<Match> matches) {
    }

    public record ProposalResult(UUID proposalId, String state) {
    }

    public record Turn(UUID userMessageId, UUID assistantMessageId, String answer, int toolCalls,
            boolean replayed) {
    }
}
