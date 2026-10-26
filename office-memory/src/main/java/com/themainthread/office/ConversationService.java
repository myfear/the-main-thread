package com.themainthread.office;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServiceUnavailableException;

import org.hibernate.Session;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class ConversationService {
    private final Session session;
    private final MemoryService memories;
    private final OfficeAssistant assistant;
    private final InvocationContext context;
    private final ReentrantLock[] locks = new ReentrantLock[64];

    public ConversationService(Session session, MemoryService memories, OfficeAssistant assistant, InvocationContext context) {
        this.session = session;
        this.memories = memories;
        this.assistant = assistant;
        this.context = context;
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public Conversation create(UUID projectId, String title) {
        return QuarkusTransaction.requiringNew().call(() -> {
            memories.requireProject(projectId);
            Conversation c = new Conversation();
            c.projectId = projectId;
            c.title = MemoryService.text(title, 120, "Conversation title");
            session.persist(c);
            return c;
        });
    }

    void recoverInterruptedTurns(@Observes StartupEvent event) {
        QuarkusTransaction.requiringNew().run(() -> {
            session.createMutationQuery("update MemoryProposal set status='REJECTED', decidedAt=:now, version=version+1 "
                    + "where status='PENDING' and sourceMessageId in (select id from ConversationMessage where state='PROCESSING')")
                    .setParameter("now", Instant.now()).executeUpdate();
            session.createMutationQuery("update ConversationMessage set state='FAILED' where state='PROCESSING'")
                    .executeUpdate();
        });
    }

    public List<ConversationMessage> messages(UUID conversationId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            memories.requireConversation(conversationId);
            return session.createSelectionQuery("from ConversationMessage where conversationId=:c order by sequence",
                    ConversationMessage.class).setParameter("c", conversationId).getResultList();
        });
    }

    public ApiViews.Turn send(UUID conversationId, String input, UUID requestId) {
        String text = MemoryService.text(input, 4000, "Message");
        if (requestId == null) {
            throw new BadRequestException("A requestId is required for retry protection.");
        }
        ReentrantLock lock = locks[Math.floorMod(conversationId.hashCode(), locks.length)];
        if (!lock.tryLock()) {
            throw MemoryService.conflict("A turn is already running. Wait for it to finish.");
        }
        try {
            ConversationMessage user = QuarkusTransaction.requiringNew().call(() -> {
                Conversation c = memories.requireConversation(conversationId);
                List<ConversationMessage> existing = session.createSelectionQuery(
                        "from ConversationMessage where conversationId=:c and requestId=:r", ConversationMessage.class)
                        .setParameter("c", conversationId).setParameter("r", requestId).getResultList();
                if (!existing.isEmpty()) {
                    ConversationMessage saved = existing.getFirst();
                    if (!saved.content.equals(text)) {
                        throw MemoryService.conflict("This requestId belongs to a different message.");
                    }
                    if (!saved.state.equals("COMPLETE")) {
                        throw MemoryService
                                .conflict("The prior attempt did not complete. Send a new turn with a new requestId.");
                    }
                    return saved;
                }
                ConversationMessage m = new ConversationMessage();
                m.conversationId = c.id;
                m.role = "USER";
                m.content = text;
                m.requestId = requestId;
                m.sequence = nextSequence(c.id);
                m.state = "PROCESSING";
                session.persist(m);
                return m;
            });
            if (user.state.equals("COMPLETE")) {
                return result(user.id, true);
            }
            Conversation c = QuarkusTransaction.requiringNew().call(() -> memories.requireConversation(conversationId));
            context.begin(c.id, c.projectId, user.id);
            try {
                String answer = assistant.chat(c.id, text);
                if (answer == null || answer.isBlank()) {
                    throw new IllegalStateException("The model returned no answer.");
                }
                QuarkusTransaction.requiringNew().run(() -> {
                    session.find(ConversationMessage.class, user.id).state = "COMPLETE";
                    ConversationMessage response = new ConversationMessage();
                    response.conversationId = c.id;
                    response.role = "ASSISTANT";
                    response.content = answer;
                    response.sequence = nextSequence(c.id);
                    response.replyToId = user.id;
                    session.persist(response);
                    session.find(Conversation.class, c.id).updatedAt = Instant.now();
                });
                return result(user.id, false);
            } catch (RuntimeException e) {
                QuarkusTransaction.requiringNew().run(() -> {
                    session.find(ConversationMessage.class, user.id).state = "FAILED";
                    session.createMutationQuery(
                            "update MemoryProposal set status='REJECTED', decidedAt=:now, version=version+1 "
                                    + "where sourceMessageId=:m and status='PENDING'")
                            .setParameter("now", Instant.now()).setParameter("m", user.id).executeUpdate();
                });
                throw new ServiceUnavailableException(
                        "The assistant could not finish this turn. Its proposals were rejected. Check Ollama, then send a new turn.");
            }
        } finally {
            lock.unlock();
        }
    }

    private ApiViews.Turn result(UUID sourceId, boolean replayed) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ConversationMessage reply = session.createSelectionQuery("from ConversationMessage where replyToId=:id",
                    ConversationMessage.class).setParameter("id", sourceId).getSingleResult();
            long count = session
                    .createSelectionQuery("select count(*) from ToolInvocation where sourceMessageId=:id", Long.class)
                    .setParameter("id", sourceId).getSingleResult();
            return new ApiViews.Turn(sourceId, reply.id, reply.content, Math.toIntExact(count), replayed);
        });
    }

    private long nextSequence(UUID conversationId) {
        return session
                .createSelectionQuery("select coalesce(max(sequence), 0) + 1 from ConversationMessage where conversationId=:c",
                        Long.class)
                .setParameter("c", conversationId).getSingleResult();
    }
}
