package com.themainthread.office;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;

import org.hibernate.Session;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;

import io.quarkus.narayana.jta.QuarkusTransaction;

@ApplicationScoped
public class OfficeMemoryTools {
    private final InvocationContext context;
    private final MemoryService memories;
    private final Session session;

    public OfficeMemoryTools(InvocationContext context, MemoryService memories, Session session) {
        this.context = context;
        this.memories = memories;
        this.session = session;
    }

    @Tool(name = "memory_search", value = "Search ACTIVE approved facts in this project before answering about past decisions. No matches means no saved evidence.")
    public Object search(@ToolMemoryId Object memoryId, String query,
            @P(value = "Maximum matches, from 1 to 5. Defaults to 5.", required = false) Integer limit) {
        return invoke(memoryId, "memory_search", () -> memories.search(context.projectId(), query, limit == null ? 5 : limit));
    }

    @Tool(name = "memory_propose_remember", value = "Propose ONE atomic project fact for human approval. Creates PENDING state only. Split a city, date, and headcount into separate calls.")
    public Object remember(@ToolMemoryId Object memoryId, String text, MemoryKind kind, String reason) {
        return invoke(memoryId, "memory_propose_remember", () -> memories.propose(context.conversationId(),
                context.messageId(), MemoryOperation.REMEMBER, null, text, kind, reason));
    }

    @Tool(name = "memory_propose_supersede", value = "Propose replacing an ACTIVE fact identified by memory_search. Pending human approval; does not change it immediately.")
    public Object replace(@ToolMemoryId Object memoryId, String target_memory_id, String replacement_text, String reason) {
        return invoke(memoryId, "memory_propose_supersede", () -> memories.propose(context.conversationId(),
                context.messageId(), MemoryOperation.SUPERSEDE, target(target_memory_id), replacement_text, null, reason));
    }

    @Tool(name = "memory_propose_forget", value = "Propose removing a saved fact and its saved versions, using an ID from memory_search. Requires human approval. Conversation history is retained.")
    public Object forget(@ToolMemoryId Object memoryId, String target_memory_id, String reason) {
        return invoke(memoryId, "memory_propose_forget", () -> memories.propose(context.conversationId(),
                context.messageId(), MemoryOperation.FORGET, target(target_memory_id), null, null, reason));
    }

    private UUID target(String value) {
        try {
            UUID id = UUID.fromString(value);
            context.requireRetrieved(id);
            return id;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Use a memory ID returned by memory_search.");
        }
    }

    private Object invoke(Object memoryId, String name, Supplier<Object> operation) {
        context.verify(memoryId);
        long started = System.nanoTime();
        String outcome = "SUCCESS";
        String description = "Pending approval";
        try {
            Object result = operation.get();
            if (result instanceof ApiViews.SearchResult search) {
                context.recordSearch(search);
                description = search.matches().size() + " approved matches";
                if (search.matches().isEmpty()) {
                    outcome = "ZERO_RESULTS";
                }
            }
            return result;
        } catch (WebApplicationException e) {
            outcome = "REJECTED";
            description = "Request rejected by application validation";
            return Map.of("error", e.getMessage());
        } catch (RuntimeException e) {
            outcome = "FAILED";
            description = "Tool unavailable; no change approved";
            return Map.of("error", description);
        } finally {
            String finalOutcome = outcome;
            String finalDescription = description;
            QuarkusTransaction.requiringNew().run(() -> {
                ToolInvocation call = new ToolInvocation();
                call.conversationId = context.conversationId();
                call.sourceMessageId = context.messageId();
                call.toolName = name;
                call.durationMs = (System.nanoTime() - started) / 1_000_000;
                call.outcome = finalOutcome;
                call.description = finalDescription;
                session.persist(call);
            });
        }
    }
}
