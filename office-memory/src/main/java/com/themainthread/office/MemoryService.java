package com.themainthread.office;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;

import org.hibernate.Session;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;

import io.quarkus.narayana.jta.QuarkusTransaction;

@ApplicationScoped
public class MemoryService {
    private final Session session;
    private final EmbeddingService embeddings;
    private final HibernateEmbeddingStore<MemoryRecord> store;
    private final OfficeConfig config;

    public MemoryService(Session session, EmbeddingService embeddings,
            HibernateEmbeddingStore<MemoryRecord> store, OfficeConfig config) {
        this.session = session;
        this.embeddings = embeddings;
        this.store = store;
        this.config = config;
    }

    public ApiViews.SearchResult search(UUID projectId, String query, int limit) {
        String normalized = text(query, 500, "Search query");
        if (limit < 1 || limit > 5) {
            throw new BadRequestException("Search limit must be between 1 and 5.");
        }
        Embedding vector = embeddings.query(normalized);
        return QuarkusTransaction.requiringNew().call(() -> {
            requireProject(projectId);
            var filter = MetadataFilterBuilder.metadataKey("projectId").isEqualTo(projectId)
                    .and(MetadataFilterBuilder.metadataKey("status").isEqualTo("ACTIVE"));
            var result = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(vector).maxResults(limit).minScore(config.minimumScore()).filter(filter).build());
            return new ApiViews.SearchResult(result.matches().stream().map(match -> {
                MemoryRecord m = session.find(MemoryRecord.class, UUID.fromString(match.embeddingId()));
                return new ApiViews.Match(m.id, m.kind, m.text, m.sourceConversationId,
                        m.sourceMessageId, match.score());
            }).toList());
        });
    }

    public ApiViews.ProposalResult propose(UUID conversationId, UUID sourceMessageId, MemoryOperation operation,
            UUID targetId, String proposedText, MemoryKind kind, String reason) {
        String value = operation == MemoryOperation.FORGET ? null : text(proposedText, 1000, "Saved fact");
        String explanation = text(reason, 500, "Reason");
        if (kind == null && operation == MemoryOperation.REMEMBER) {
            throw new BadRequestException("Choose FACT, DECISION, or PREFERENCE.");
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            Conversation c = requireConversation(conversationId);
            session.find(Project.class, c.projectId, LockModeType.PESSIMISTIC_WRITE);
            ConversationMessage source = session.find(ConversationMessage.class, sourceMessageId);
            if (source == null || !source.conversationId.equals(c.id) || !source.role.equals("USER")
                    || source.state.equals("FAILED")) {
                throw new BadRequestException("Proposal source must be a user message in this conversation.");
            }
            MemoryRecord target = operation == MemoryOperation.REMEMBER ? null : activeTarget(c.projectId, targetId);
            String key = digest(operation + ":" + targetId + ":" + kind + ":" + value);
            List<MemoryProposal> existing = session.createSelectionQuery(
                    "from MemoryProposal where projectId=:p and dedupKey=:k and (sourceMessageId=:s or status='PENDING')",
                    MemoryProposal.class)
                    .setParameter("p", c.projectId).setParameter("s", source.id).setParameter("k", key).getResultList();
            if (!existing.isEmpty()) {
                return new ApiViews.ProposalResult(existing.getFirst().id, existing.getFirst().status);
            }
            MemoryProposal proposal = new MemoryProposal();
            proposal.projectId = c.projectId;
            proposal.factId = target == null ? UUID.randomUUID() : target.factId;
            proposal.sourceConversationId = c.id;
            proposal.sourceMessageId = source.id;
            proposal.operation = operation;
            proposal.targetMemoryId = target == null ? null : target.id;
            proposal.targetVersion = target == null ? null : target.version;
            proposal.proposedText = value;
            proposal.kind = target == null ? kind : target.kind;
            proposal.reason = explanation;
            proposal.dedupKey = key;
            session.persist(proposal);
            return new ApiViews.ProposalResult(proposal.id, proposal.status);
        });
    }

    public ApiViews.Proposal approve(UUID projectId, UUID proposalId, long expectedVersion) {
        ApiViews.Proposal before = QuarkusTransaction.requiringNew()
                .call(() -> ApiViews.Proposal.from(requireProposal(projectId, proposalId, false)));
        if (before.status().equals("APPROVED")) {
            return before;
        }
        if (!before.status().equals("PENDING") || before.version() != expectedVersion) {
            throw conflict("This proposal changed. Refresh pending changes.");
        }
        // Model I/O happens before the transaction that changes active facts.
        Embedding vector = before.operation() == MemoryOperation.FORGET ? null : embeddings.document(before.proposedText());
        return QuarkusTransaction.requiringNew().call(() -> {
            session.find(Project.class, projectId, LockModeType.PESSIMISTIC_WRITE);
            MemoryProposal p = requireProposal(projectId, proposalId, true);
            if (p.status.equals("APPROVED")) {
                return ApiViews.Proposal.from(p);
            }
            if (!p.status.equals("PENDING") || p.version != expectedVersion) {
                throw conflict("This proposal changed. Refresh pending changes.");
            }
            ConversationMessage source = session.find(ConversationMessage.class, p.sourceMessageId);
            if (!source.state.equals("COMPLETE")) {
                throw conflict("Wait for the conversation turn to finish before approving.");
            }
            MemoryRecord target = null;
            if (p.operation != MemoryOperation.REMEMBER) {
                target = session.find(MemoryRecord.class, p.targetMemoryId, LockModeType.PESSIMISTIC_WRITE);
                if (target == null || !target.projectId.equals(projectId) || !target.status.equals("ACTIVE")
                        || target.version != p.targetVersion) {
                    throw conflict("The saved fact changed or was removed. Reject this proposal and ask again.");
                }
            }
            if (p.operation == MemoryOperation.FORGET) {
                removeFact(p);
            } else {
                MemoryRecord replacement = new MemoryRecord();
                replacement.factId = p.factId;
                replacement.projectId = projectId;
                replacement.kind = p.kind;
                replacement.text = p.proposedText;
                replacement.embedding = vector.vector();
                replacement.sourceConversationId = p.sourceConversationId;
                replacement.sourceMessageId = p.sourceMessageId;
                if (target != null) {
                    target.status = "SUPERSEDED";
                    target.supersededAt = Instant.now();
                    target.supersededById = replacement.id;
                    session.flush();
                }
                session.persist(replacement);
                p.resultMemoryId = replacement.id;
            }
            p.status = "APPROVED";
            p.decidedAt = Instant.now();
            audit(p, p.operation.name());
            session.flush();
            return ApiViews.Proposal.from(p);
        });
    }

    private void removeFact(MemoryProposal removal) {
        session.createMutationQuery("delete from MemoryRecord where projectId=:p and factId=:f")
                .setParameter("p", removal.projectId).setParameter("f", removal.factId).executeUpdate();
        for (MemoryProposal related : session.createSelectionQuery(
                "from MemoryProposal where projectId=:p and factId=:f", MemoryProposal.class)
                .setParameter("p", removal.projectId).setParameter("f", removal.factId).getResultList()) {
            related.proposedText = null;
            related.reason = null;
            related.dedupKey = digest(related.id.toString());
            if (related.status.equals("PENDING") && !related.id.equals(removal.id)) {
                related.status = "REJECTED";
                related.decidedAt = Instant.now();
            }
        }
    }

    public ApiViews.Proposal reject(UUID projectId, UUID proposalId, long expectedVersion) {
        return QuarkusTransaction.requiringNew().call(() -> {
            session.find(Project.class, projectId, LockModeType.PESSIMISTIC_WRITE);
            MemoryProposal p = requireProposal(projectId, proposalId, true);
            if (p.status.equals("REJECTED")) {
                return ApiViews.Proposal.from(p);
            }
            if (!p.status.equals("PENDING") || p.version != expectedVersion) {
                throw conflict("This proposal changed. Refresh pending changes.");
            }
            p.status = "REJECTED";
            p.decidedAt = Instant.now();
            audit(p, "REJECT");
            session.flush();
            return ApiViews.Proposal.from(p);
        });
    }

    public List<ApiViews.Fact> facts(UUID projectId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            requireProject(projectId);
            return session.createSelectionQuery("from MemoryRecord where projectId=:p and status='ACTIVE' order by createdAt",
                    MemoryRecord.class).setParameter("p", projectId).getResultList().stream().map(ApiViews.Fact::from).toList();
        });
    }

    public List<ApiViews.Fact> history(UUID projectId, UUID memoryId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            MemoryRecord m = session.find(MemoryRecord.class, memoryId);
            if (m == null || !m.projectId.equals(projectId)) {
                throw new NotFoundException("Saved fact not found in this project.");
            }
            return session.createSelectionQuery("from MemoryRecord where projectId=:p and factId=:f order by createdAt",
                    MemoryRecord.class).setParameter("p", projectId).setParameter("f", m.factId)
                    .getResultList().stream().map(ApiViews.Fact::from).toList();
        });
    }

    public List<ApiViews.Proposal> proposals(UUID projectId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            requireProject(projectId);
            return session
                    .createSelectionQuery("from MemoryProposal where projectId=:p and status='PENDING' order by createdAt",
                            MemoryProposal.class)
                    .setParameter("p", projectId).getResultList().stream().map(ApiViews.Proposal::from).toList();
        });
    }

    Project requireProject(UUID id) {
        Project project = id == null ? null : session.find(Project.class, id);
        if (project == null) {
            throw new NotFoundException("Project not found.");
        }
        return project;
    }

    Conversation requireConversation(UUID id) {
        Conversation c = id == null ? null : session.find(Conversation.class, id);
        if (c == null) {
            throw new NotFoundException("Conversation not found.");
        }
        return c;
    }

    private MemoryProposal requireProposal(UUID projectId, UUID id, boolean lock) {
        MemoryProposal p = session.find(MemoryProposal.class, id,
                lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        if (p == null || !p.projectId.equals(projectId)) {
            throw new NotFoundException("Proposal not found in this project.");
        }
        return p;
    }

    private MemoryRecord activeTarget(UUID projectId, UUID id) {
        MemoryRecord m = id == null ? null : session.find(MemoryRecord.class, id);
        if (m == null || !m.projectId.equals(projectId)) {
            throw new NotFoundException("Saved fact not found in this project.");
        }
        if (!m.status.equals("ACTIVE")) {
            throw conflict("This saved fact has been replaced.");
        }
        return m;
    }

    private void audit(MemoryProposal p, String action) {
        MemoryAuditEvent event = new MemoryAuditEvent();
        event.projectId = p.projectId;
        event.proposalId = p.id;
        event.memoryId = p.resultMemoryId;
        event.action = action;
        session.persist(event);
    }

    static String text(String value, int max, String label) {
        if (value == null || value.isBlank() || value.strip().length() > max) {
            throw new BadRequestException(label + " must contain 1–" + max + " characters.");
        }
        return value.strip();
    }

    static ClientErrorException conflict(String message) {
        return new ClientErrorException(message, 409);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
