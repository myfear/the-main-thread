package com.themainthread.office;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;

import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OfficeMemoryTest {
    static final UUID OFFSITE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID BRIEFING = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Inject
    Session session;
    @Inject
    MemoryService memories;
    @Inject
    ConversationService conversations;
    @Inject
    HibernateChatMemoryStore window;
    @Inject
    OfficeChatMemoryProvider provider;
    @Inject
    ScriptedChatModel chat;
    @Inject
    DeterministicEmbeddingModel embeddings;
    @Inject
    EmbeddingService embeddingService;

    @BeforeEach
    void reset() {
        tx(() -> {
            for (String entity : List.of("MemoryAuditEvent", "ToolInvocation", "MemoryProposal", "MemoryRecord",
                    "ChatMemorySnapshot")) {
                session.createMutationQuery("delete from " + entity).executeUpdate();
            }
            session.createMutationQuery("delete from ConversationMessage where role='ASSISTANT'").executeUpdate();
            session.createMutationQuery("delete from ConversationMessage").executeUpdate();
            session.createMutationQuery("delete from Conversation").executeUpdate();
            return null;
        });
        chat.clear();
        embeddings.reset();
    }

    @Test
    void startsWithOnlyTwoFictionalProjectsAndNoFacts() {
        given().get("/api/projects").then().statusCode(200).body("size()", equalTo(2));
        assertTrue(memories.facts(OFFSITE).isEmpty());
        assertTrue(memories.facts(BRIEFING).isEmpty());
        given().get("/").then().statusCode(200).body(containsString("Saved facts"));
    }

    @Test
    void approvalIsExplicitAndRetryReturnsTheSameFact() {
        Conversation c = conversation(OFFSITE);
        UUID p = remember(c, "The offsite location is Hamburg.");
        assertTrue(memories.facts(OFFSITE).isEmpty());
        assertTrue(memories.search(OFFSITE, "offsite", 5).matches().isEmpty());
        ApiViews.Proposal approved = approve(p, OFFSITE);
        ApiViews.Proposal retry = memories.approve(OFFSITE, p, 0);
        assertEquals(approved.resultMemoryId(), retry.resultMemoryId());
        assertEquals(1, memories.facts(OFFSITE).size());
        assertEquals(1L, count("MemoryAuditEvent"));
        assertTrue(embeddings.inputs.stream().anyMatch(s -> s.startsWith("search_document: ")));
        assertTrue(embeddings.inputs.stream().anyMatch(s -> s.startsWith("search_query: ")));
    }

    @Test
    void databaseFiltersBothProjectAndStatusBeforeLimiting() {
        Conversation other = conversation(BRIEFING);
        for (int i = 0; i < 7; i++) {
            approve(remember(other, "Identical fact " + i), BRIEFING);
        }
        Conversation c = conversation(OFFSITE);
        UUID old = approve(remember(c, "The offsite location is Hamburg."), OFFSITE).resultMemoryId();
        UUID replacement = propose(c, MemoryOperation.SUPERSEDE, old, "The offsite location is Berlin.");
        UUID active = approve(replacement, OFFSITE).resultMemoryId();
        var matches = memories.search(OFFSITE, "offsite location", 1).matches();
        assertEquals(1, matches.size());
        assertEquals(active, matches.getFirst().id());
        assertEquals("The offsite location is Berlin.", matches.getFirst().text());
    }

    @Test
    void replacementPreservesIndependentFactsAndHistory() {
        Conversation c = conversation(OFFSITE);
        UUID old = approve(remember(c, "The offsite location is Hamburg."), OFFSITE).resultMemoryId();
        UUID date = approve(remember(c, "The offsite is on 19 November 2026."), OFFSITE).resultMemoryId();
        approve(propose(c, MemoryOperation.SUPERSEDE, old, "The offsite location is Berlin."), OFFSITE);
        assertEquals(2, memories.facts(OFFSITE).size());
        assertTrue(memories.facts(OFFSITE).stream().anyMatch(m -> m.id().equals(date)));
        var history = memories.history(OFFSITE, old);
        assertEquals(2, history.size());
        assertEquals("SUPERSEDED", history.getFirst().status());
        assertEquals(history.getLast().id(), history.getFirst().supersededById());
    }

    @Test
    void forgetPurgesAllSavedVersionsAndProposalCopiesButKeepsTranscript() {
        Conversation c = conversation(OFFSITE);
        UUID firstProposal = remember(c, "The offsite location is Hamburg.");
        UUID old = approve(firstProposal, OFFSITE).resultMemoryId();
        UUID correction = propose(c, MemoryOperation.SUPERSEDE, old, "The offsite location is Berlin.");
        UUID current = approve(correction, OFFSITE).resultMemoryId();
        UUID pendingCorrection = propose(c, MemoryOperation.SUPERSEDE, current, "The offsite location is Munich.");
        UUID forget = propose(c, MemoryOperation.FORGET, current, null);
        approve(forget, OFFSITE);
        assertEquals(0, count("MemoryRecord"));
        assertTrue(memories.search(OFFSITE, "location", 5).matches().isEmpty());
        for (UUID id : List.of(firstProposal, correction, pendingCorrection, forget)) {
            MemoryProposal p = tx(() -> session.find(MemoryProposal.class, id));
            assertEquals(null, p.proposedText);
            assertEquals(null, p.reason);
        }
        assertEquals("REJECTED", tx(() -> session.find(MemoryProposal.class, pendingCorrection).status));
        assertFalse(conversations.messages(c.id).isEmpty());
        assertThrows(NotFoundException.class, () -> memories.history(OFFSITE, old));
        assertEquals(3L, count("MemoryAuditEvent"));
    }

    @Test
    void staleCorrectionCannotOverwriteANewerDecision() {
        Conversation c = conversation(OFFSITE);
        UUID old = approve(remember(c, "Location is Hamburg."), OFFSITE).resultMemoryId();
        UUID p1 = propose(c, MemoryOperation.SUPERSEDE, old, "Location is Berlin.");
        UUID p2 = propose(c, MemoryOperation.SUPERSEDE, old, "Location is Munich.");
        approve(p1, OFFSITE);
        ClientErrorException error = assertThrows(ClientErrorException.class, () -> approve(p2, OFFSITE));
        assertEquals(409, error.getResponse().getStatus());
        assertEquals("Location is Berlin.", memories.facts(OFFSITE).getFirst().text());
    }

    @Test
    void concurrentApprovalCreatesExactlyOneFact() {
        UUID p = remember(conversation(OFFSITE), "The headcount is 24.");
        var a = CompletableFuture.supplyAsync(() -> memories.approve(OFFSITE, p, 0));
        var b = CompletableFuture.supplyAsync(() -> memories.approve(OFFSITE, p, 0));
        assertEquals(a.join().resultMemoryId(), b.join().resultMemoryId());
        assertEquals(1, memories.facts(OFFSITE).size());
        assertEquals(1L, count("MemoryAuditEvent"));
    }

    @Test
    void concurrentReplacementAndRemovalHaveOneWinnerAndOneConflict() {
        Conversation c = conversation(OFFSITE);
        UUID target = approve(remember(c, "Location is Hamburg."), OFFSITE).resultMemoryId();
        UUID replacement = propose(c, MemoryOperation.SUPERSEDE, target, "Location is Berlin.");
        UUID removal = propose(c, MemoryOperation.FORGET, target, null);
        var a = CompletableFuture.supplyAsync(() -> approvalStatus(replacement));
        var b = CompletableFuture.supplyAsync(() -> approvalStatus(removal));
        assertEquals(List.of(200, 409), List.of(a.join(), b.join()).stream().sorted().toList());
        assertTrue(memories.facts(OFFSITE).size() <= 1);
    }

    private int approvalStatus(UUID proposal) {
        try {
            memories.approve(OFFSITE, proposal, 0);
            return 200;
        } catch (ClientErrorException e) {
            return e.getResponse().getStatus();
        }
    }

    @Test
    void rejectedProposalAndEmbeddingFailureLeaveFactsUnchanged() {
        UUID p = remember(conversation(OFFSITE), "The headcount is 24.");
        embeddings.fail = true;
        assertThrows(ServiceUnavailableException.class, () -> approve(p, OFFSITE));
        assertEquals("PENDING", tx(() -> session.find(MemoryProposal.class, p).status));
        assertTrue(memories.facts(OFFSITE).isEmpty());
        embeddings.fail = false;
        memories.reject(OFFSITE, p, 0);
        assertTrue(memories.facts(OFFSITE).isEmpty());
        assertEquals("REJECTED", tx(() -> session.find(MemoryProposal.class, p).status));
    }

    @Test
    void rejectsInvalidInputsCrossProjectTargetsAndSpoofedSources() {
        Conversation c = conversation(OFFSITE);
        Conversation other = conversation(BRIEFING);
        UUID target = approve(remember(other, "The review style is concise."), BRIEFING).resultMemoryId();
        assertThrows(NotFoundException.class, () -> propose(c, MemoryOperation.FORGET, target, null));
        assertThrows(NotFoundException.class, () -> propose(c, MemoryOperation.FORGET, UUID.randomUUID(), null));
        assertThrows(BadRequestException.class, () -> remember(c, "x".repeat(1001)));
        assertThrows(BadRequestException.class, () -> memories.search(OFFSITE, " ", 5));
        assertThrows(BadRequestException.class, () -> memories.search(OFFSITE, "x".repeat(501), 5));
        assertThrows(BadRequestException.class, () -> memories.search(OFFSITE, "offsite", 6));
        UUID source = source(other, "Remember something");
        assertThrows(BadRequestException.class, () -> memories.propose(c.id, source, MemoryOperation.REMEMBER,
                null, "Spoofed fact", MemoryKind.FACT, "user requested"));
    }

    @Test
    void repeatedToolProposalForTheSameSourceIsDeduplicated() {
        Conversation c = conversation(OFFSITE);
        UUID source = source(c, "Remember Hamburg");
        var a = memories.propose(c.id, source, MemoryOperation.REMEMBER, null, "Location is Hamburg.", MemoryKind.FACT,
                "request");
        var b = memories.propose(c.id, source, MemoryOperation.REMEMBER, null, "Location is Hamburg.", MemoryKind.FACT,
                "request");
        assertEquals(a.proposalId(), b.proposalId());
        assertEquals(1, memories.proposals(OFFSITE).size());
        UUID secondSource = source(c, "Remember the same location again");
        var repeated = memories.propose(c.id, secondSource, MemoryOperation.REMEMBER, null,
                "Location is Hamburg.", MemoryKind.FACT, "request");
        assertEquals(a.proposalId(), repeated.proposalId());
        assertEquals(source, memories.proposals(OFFSITE).getFirst().sourceMessageId());
    }

    @Test
    void restartRecoveryRejectsProposalsFromInterruptedTurns() {
        Conversation c = conversation(OFFSITE);
        UUID source = source(c, "Remember Hamburg");
        UUID p = memories.propose(c.id, source, MemoryOperation.REMEMBER, null,
                "Location is Hamburg.", MemoryKind.FACT, "request").proposalId();
        tx(() -> {
            session.find(ConversationMessage.class, source).state = "PROCESSING";
            return null;
        });
        conversations.recoverInterruptedTurns(null);
        assertEquals("FAILED", conversations.messages(c.id).getFirst().state);
        assertEquals("REJECTED", tx(() -> session.find(MemoryProposal.class, p).status));
    }

    @Test
    void actualToolRejectsCrossProjectAndMalformedIdsWithoutMutatingFacts() {
        Conversation other = conversation(BRIEFING);
        UUID target = approve(remember(other, "Use concise slides."), BRIEFING).resultMemoryId();
        Conversation c = conversation(OFFSITE);
        chat.enqueue(request -> AiMessage.from(tool("memory_propose_forget",
                "{\"target_memory_id\":\"" + target + "\",\"reason\":\"test\"}")));
        chat.enqueue(request -> AiMessage.from("The fact is not in this project."));
        assertEquals(1, send(c.id, "Remove that fact", UUID.randomUUID()).toolCalls());
        assertTrue(memories.proposals(OFFSITE).isEmpty());
        assertEquals(1, memories.facts(BRIEFING).size());
        chat.enqueue(request -> AiMessage.from(tool("memory_propose_forget",
                "{\"target_memory_id\":\"not-a-uuid\",\"reason\":\"test\"}")));
        chat.enqueue(request -> AiMessage.from("An ID from search is required."));
        send(c.id, "Remove that fact", UUID.randomUUID());
        assertEquals(2L,
                tx(() -> session
                        .createSelectionQuery("select count(*) from ToolInvocation where outcome='REJECTED'", Long.class)
                        .getSingleResult()));
    }

    @Test
    void workingWindowRoundTripsToolMessagesAndEvictsInsteadOfGrowingHistory() {
        Conversation c = conversation(OFFSITE);
        ToolExecutionRequest tool = tool("memory_search", "{\"query\":\"offsite\",\"limit\":5}");
        List<ChatMessage> messages = List.of(SystemMessage.from("system"), UserMessage.from("Where?"),
                AiMessage.from(tool), ToolExecutionResultMessage.from(tool, "No matches"), AiMessage.from("No saved fact"));
        window.updateMessages(c.id, messages);
        assertEquals(messages, window.getMessages(c.id));
        var memory = provider.get(c.id);
        for (int i = 0; i < 20; i++) {
            memory.add(UserMessage.from("message " + i));
        }
        assertTrue(window.getMessages(c.id).size() <= 12);
        assertFalse(window.getMessages(c.id).contains(UserMessage.from("Where?")));
        var reloaded = new HibernateChatMemoryStore(session);
        assertEquals(window.getMessages(c.id), reloaded.getMessages(c.id));
        window.deleteMessages(c.id);
        assertTrue(window.getMessages(c.id).isEmpty());
    }

    @Test
    void conversationsHaveIndependentWorkingWindows() {
        Conversation a = conversation(OFFSITE);
        Conversation b = conversation(OFFSITE);
        assertNotEquals(a.id, b.id);
        window.updateMessages(a.id, List.of(UserMessage.from("Only in A")));
        assertTrue(window.getMessages(b.id).isEmpty());
    }

    @Test
    void actualAiServiceInvokesToolWithServerOwnedSourceAndProject() {
        Conversation c = conversation(OFFSITE);
        chat.enqueue(request -> {
            assertEquals(4, request.toolSpecifications().size());
            request.toolSpecifications().forEach(spec -> {
                String schema = spec.parameters().toString();
                assertFalse(schema.contains("projectId"));
                assertFalse(schema.contains("sourceMessageId"));
                assertFalse(schema.contains("conversationId"));
            });
            return AiMessage.from(tool("memory_propose_remember",
                    "{\"text\":\"The offsite location is Hamburg.\",\"kind\":\"FACT\",\"reason\":\"User requested this\"}"));
        });
        chat.enqueue(request -> {
            assertTrue(request.messages().stream().anyMatch(m -> m instanceof ToolExecutionResultMessage));
            return AiMessage.from("The location is pending approval.");
        });
        UUID requestId = UUID.randomUUID();
        ApiViews.Turn turn = send(c.id, "Remember Hamburg.", requestId);
        assertEquals(1, turn.toolCalls());
        assertEquals(turn.userMessageId(), memories.proposals(OFFSITE).getFirst().sourceMessageId());
        assertTrue(memories.facts(OFFSITE).isEmpty());
        ApiViews.Turn retry = send(c.id, "Remember Hamburg.", requestId);
        assertTrue(retry.replayed());
        assertEquals(turn.assistantMessageId(), retry.assistantMessageId());
        assertEquals(2, conversations.messages(c.id).size());
        assertTrue(window.getMessages(c.id).stream().anyMatch(m -> m instanceof ToolExecutionResultMessage));
    }

    @Test
    void actualSearchToolReportsZeroResultsDifferentlyFromNoToolCall() {
        Conversation c = conversation(OFFSITE);
        chat.enqueue(request -> AiMessage.from(tool("memory_search", "{\"query\":\"headcount\",\"limit\":5}")));
        chat.enqueue(request -> AiMessage.from("No saved headcount."));
        ApiViews.Turn turn = send(c.id, "What is the headcount?", UUID.randomUUID());
        assertEquals(1, turn.toolCalls());
        assertEquals("ZERO_RESULTS", tx(() -> session.createSelectionQuery("from ToolInvocation", ToolInvocation.class)
                .getSingleResult().outcome));
        chat.enqueue(request -> AiMessage.from("Hello."));
        assertEquals(0, send(c.id, "Hello", UUID.randomUUID()).toolCalls());
    }

    @Test
    void failedFinalModelResponseRejectsItsProposalsAndPreservesPriorWindow() {
        Conversation c = conversation(OFFSITE);
        window.updateMessages(c.id, List.of(UserMessage.from("Prior successful context")));
        var before = window.getMessages(c.id);
        chat.enqueue(request -> AiMessage.from(tool("memory_propose_remember",
                "{\"text\":\"Location is Hamburg.\",\"kind\":\"FACT\",\"reason\":\"request\"}")));
        chat.enqueue(request -> {
            throw new IllegalStateException("Model failed after the tool");
        });
        given().contentType("application/json").body(Map.of("text", "Remember Hamburg", "requestId", UUID.randomUUID()))
                .post("/api/conversations/" + c.id + "/messages").then().statusCode(503);
        assertTrue(memories.proposals(OFFSITE).isEmpty());
        assertEquals("REJECTED",
                tx(() -> session.createSelectionQuery("from MemoryProposal", MemoryProposal.class).getSingleResult().status));
        assertEquals("FAILED", conversations.messages(c.id).getFirst().state);
        assertEquals(1, conversations.messages(c.id).size());
        assertEquals(before, window.getMessages(c.id));
    }

    @Test
    void dimensionMismatchFailsClearly() {
        embeddings.dimensions = 384;
        var error = assertThrows(IllegalStateException.class, () -> embeddingService.query("offsite"));
        assertTrue(error.getMessage().contains("768-dimensional"));
    }

    @Test
    void httpValidationAndProjectScopedApprovalRejectMalformedRequests() {
        given().contentType("application/json").body(Map.of("projectId", OFFSITE, "title", " "))
                .post("/api/conversations").then().statusCode(400);
        Conversation c = conversation(OFFSITE);
        given().contentType("application/json").body(Map.of("text", "hi"))
                .post("/api/conversations/" + c.id + "/messages").then().statusCode(400);
        UUID p = remember(c, "The headcount is 24.");
        given().contentType("application/json").body(Map.of("projectId", BRIEFING, "expectedVersion", 0))
                .post("/api/proposals/" + p + "/approve").then().statusCode(404);
        given().contentType("application/json").body(Map.of("projectId", OFFSITE, "expectedVersion", 5))
                .post("/api/proposals/" + p + "/approve").then().statusCode(409);
    }

    private Conversation conversation(UUID project) {
        return conversations.create(project, "Test conversation " + UUID.randomUUID());
    }

    private UUID source(Conversation c, String text) {
        return tx(() -> {
            ConversationMessage m = new ConversationMessage();
            m.conversationId = c.id;
            m.role = "USER";
            m.content = text;
            m.sequence = session
                    .createSelectionQuery(
                            "select coalesce(max(sequence), 0)+1 from ConversationMessage where conversationId=:c", Long.class)
                    .setParameter("c", c.id).getSingleResult();
            session.persist(m);
            return m.id;
        });
    }

    private UUID remember(Conversation c, String text) {
        return propose(c, MemoryOperation.REMEMBER, null, text);
    }

    private UUID propose(Conversation c, MemoryOperation operation, UUID target, String text) {
        UUID sourceId = source(c, text == null ? "Please forget the saved fact." : text);
        return memories.propose(c.id, sourceId, operation, target, text, MemoryKind.FACT, "Explicit user request").proposalId();
    }

    private ApiViews.Proposal approve(UUID proposal, UUID project) {
        return memories.approve(project, proposal, 0);
    }

    private ApiViews.Turn send(UUID conversation, String text, UUID requestId) {
        return given().contentType("application/json").body(Map.of("text", text, "requestId", requestId))
                .post("/api/conversations/" + conversation + "/messages").then().statusCode(200)
                .extract().as(ApiViews.Turn.class);
    }

    private long count(String entity) {
        return tx(() -> session.createSelectionQuery("select count(*) from " + entity, Long.class).getSingleResult());
    }

    private <T> T tx(Supplier<T> action) {
        return QuarkusTransaction.requiringNew().call(action::get);
    }

    private static ToolExecutionRequest tool(String name, String arguments) {
        return ToolExecutionRequest.builder().id(UUID.randomUUID().toString()).name(name).arguments(arguments).build();
    }
}
