package dev.mainthread.fieldnotes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ReportSessionTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
    private final List<ReportSession> sessions = new ArrayList<>();

    @AfterEach
    void closeSessions() {
        sessions.forEach(ReportSession::close);
    }

    @Test
    void staleUpdateAndMalformedPatchCannotPartiallyMutateDraft() {
        ReportSession session = session();
        session.update(0, Map.of("title", "Original"));
        assertConflict(() -> session.update(0, Map.of("title", "Stale")));
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("title", "Must not stick");
        patch.put("author", "Unknown Person");
        assertThrows(IllegalArgumentException.class, () -> session.update(1, patch));
        assertEquals("Original", values(session).get("title"));
        assertEquals(1L, session.view().get("revision"));
        Map<String, Object> clearTitle = new LinkedHashMap<>();
        clearTitle.put("title", null);
        session.update(1, clearTitle);
        assertFalse(values(session).containsKey("title"));
    }

    @Test
    void changingDeliveryClearsTravelAndOnsiteAnswersAndRequiresNewAnswersOnReturn() {
        ReportSession session = session();
        session.update(0, workshop());
        session.update(1, Map.of("delivery", "online"));
        for (String field : List.of("onsiteAttendees", "travel", "tripStatus", "tripDue")) {
            assertFalse(values(session).containsKey(field), field);
            assertTrue(((List<?>) session.view().get("clearedFields")).contains(field), field);
        }
        assertEquals(18L, session.view().get("totalAttendees"));
        session.update(2, Map.of("delivery", "hybrid"));
        assertNull(session.view().get("totalAttendees"));
        assertTrue(validation(session).issues().stream().anyMatch(issue -> issue.field().equals("onsiteAttendees")));
    }

    @Test
    void deferredMetricsClearManualMeasurementsAndRemainExplicitlyPending() {
        ReportSession session = session();
        Map<String, Object> report = article();
        report.put("metrics", "manual");
        report.put("views", 0);
        report.put("measuredOn", "2026-09-14");
        session.update(0, report);
        assertTrue(validation(session).ready());
        session.update(1, Map.of("metrics", "collect later", "source", "DevJournal"));
        assertFalse(values(session).containsKey("views"));
        assertFalse(values(session).containsKey("measuredOn"));
        assertEquals("pending collection (demo only)", session.view().get("metricsStatus"));
        assertTrue(validation(session).ready());
        session.update(2, Map.of("metrics", "manual"));
        assertFalse(values(session).containsKey("source"));
        assertFalse(validation(session).ready());
    }

    @Test
    void formAnswersRetainCrossFieldErrorsForBobToCorrect() throws Exception {
        ReportSession session = session();
        session.update(0, workshop());
        session.beginRun("run-1", "Check the workshop counts.");
        CompletableFuture<Map<String, Object>> result = session.showForm(1,
                List.of("completedExercises"), "How many completed the exercise?");
        ReportSession.Form form = form(session);
        session.answer(form.id(), 1, Map.of("completedExercises", 55));
        assertEquals("submitted", result.get(1, TimeUnit.SECONDS).get("status"));
        assertEquals(55, values(session).get("completedExercises"));
        assertFalse(validation(session).ready());
        assertTrue(validation(session).issues().stream().anyMatch(issue -> issue.field().equals("completedExercises")));
        session.update(2, Map.of("completedExercises", 41));
        assertTrue(validation(session).ready());
    }

    @Test
    void malformedFormAnswerIsAtomicAndKeepsTheSameFormOpen() {
        ReportSession session = session();
        session.update(0, workshop());
        session.beginRun("run-1", "Correct attendance.");
        CompletableFuture<Map<String, Object>> result = session.showForm(1,
                List.of("onsiteAttendees", "remoteAttendees"), "What are the actual counts?");
        ReportSession.Form form = form(session);
        assertThrows(IllegalArgumentException.class,
                () -> session.answer(form.id(), 1, Map.of("onsiteAttendees", 30, "remoteAttendees", "unknown")));
        assertEquals(32, values(session).get("onsiteAttendees"));
        assertEquals(1L, session.view().get("revision"));
        assertEquals(form, form(session));
        assertFalse(result.isDone());
        assertThrows(IllegalArgumentException.class,
                () -> session.answer(form.id(), 1, Map.of("onsiteAttendees", 30)));
        assertConflict(() -> session.answer(form.id(), 0, Map.of("onsiteAttendees", 30, "remoteAttendees", 20)));
    }

    @Test
    void duplicateAnswersResolveOnceAndConflictingRetryCannotOverwrite() throws Exception {
        ReportSession session = session();
        session.beginRun("run-1", "Set a title.");
        CompletableFuture<Map<String, Object>> result = session.showForm(0, List.of("title"), "What is the title?");
        String formId = form(session).id();
        Map<String, Object> answer = Map.of("title", "Runtime workshop");
        session.answer(formId, 0, answer);
        long cursor = session.cursor();
        session.answer(formId, 0, answer);
        assertEquals(cursor, session.cursor());
        assertEquals(1L, session.view().get("revision"));
        assertEquals("submitted", result.get(1, TimeUnit.SECONDS).get("status"));
        assertConflict(() -> session.answer(formId, 0, Map.of("title", "Different")));
        assertEquals("Runtime workshop", values(session).get("title"));
    }

    @Test
    void cancellingFormPreservesDraftAndRejectsLateSubmission() throws Exception {
        ReportSession session = session();
        session.update(0, Map.of("title", "Keep this"));
        session.beginRun("run-1", "Correct the title.");
        CompletableFuture<Map<String, Object>> result = session.showForm(1, List.of("title"), "New title?");
        String formId = form(session).id();
        assertConflict(() -> session.update(1, Map.of("title", "Cannot race form")));
        session.cancelForm("cancelled");
        assertEquals("cancelled", result.get(1, TimeUnit.SECONDS).get("status"));
        assertNull(session.view().get("pendingForm"));
        assertEquals("Keep this", values(session).get("title"));
        assertConflict(() -> session.answer(formId, 1, Map.of("title", "Too late")));
    }

    @Test
    void expiredFormCompletesToolWithoutLosingDraft() throws Exception {
        ReportSession session = session(Duration.ofMillis(20));
        session.update(0, Map.of("title", "Existing title"));
        session.beginRun("run-1", "Correct the title.");
        CompletableFuture<Map<String, Object>> result = session.showForm(1, List.of("title"), "New title?");
        assertEquals("expired", result.get(3, TimeUnit.SECONDS).get("status"));
        assertNull(session.view().get("pendingForm"));
        assertEquals("Existing title", values(session).get("title"));
        assertEquals(1L, session.view().get("revision"));
    }

    @Test
    void saveRequiresCompleteFinishedReportAndIsIdempotent() {
        ReportSession session = session();
        assertConflict(() -> session.save(0));
        session.update(0, workshop());
        assertEquals("ready", session.view().get("status"));
        session.beginRun("run-1", "Review report.");
        assertConflict(() -> session.save(1));
        session.showForm(1, List.of("title"), "Confirm the title?");
        assertFalse(validation(session).ready());
        session.finish("run-1", null);
        assertTrue(validation(session).ready());
        assertEquals("saved", session.save(1).get("status"));
        long cursor = session.cursor();
        assertEquals("saved", session.save(1).get("status"));
        assertEquals(cursor, session.cursor());
        assertConflict(() -> session.update(1, Map.of("title", "Changed after save")));
    }

    @Test
    void replayPreservesRunLifecycleTextOrderAndCursorWithoutRestartingRun() {
        ReportSession session = session();
        session.beginRun("run-1", "Hello");
        session.text("run-1", "First ");
        session.text("wrong-run", "ignored");
        session.text("run-1", "second");
        session.finish("run-1", null);
        List<ReportSession.Event> events = replay(session, 0, "run-1");
        assertEquals(List.of("RUN_STARTED", "STATE_SNAPSHOT", "TEXT_MESSAGE_START", "TEXT_MESSAGE_CONTENT",
                "TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_END", "STATE_SNAPSHOT", "RUN_FINISHED"),
                events.stream().map(event -> event.data().get("type")).toList());
        for (int i = 1; i < events.size(); i++) assertEquals(events.get(i - 1).sequence() + 1, events.get(i).sequence());
        long cursor = events.get(3).sequence();
        assertEquals(events.subList(4, events.size()), replay(session, cursor, "run-1"));
        assertConflict(() -> session.beginRun("run-1", "Duplicate prompt"));
        session.beginRun("run-2", "Next turn");
        session.finish("run-2", null);
        assertTrue(replay(session, 0, "run-2").stream().allMatch(event -> event.runId().equals("run-2")));
        assertEquals(events, replay(session, 0, "run-1"));
    }

    private ReportSession session() {
        return session(Duration.ofMinutes(5));
    }

    private ReportSession session(Duration timeout) {
        ReportSession session = new ReportSession(YearMonth.of(2026, 9), "Taylor Quinn", CLOCK, timeout);
        sessions.add(session);
        return session;
    }

    private static List<ReportSession.Event> replay(ReportSession session, long cursor, String runId) {
        return session.stream(cursor, runId).collect().asList().await().atMost(Duration.ofSeconds(1));
    }

    private static Map<?, ?> values(ReportSession session) {
        return (Map<?, ?>) session.view().get("values");
    }

    private static ReportSession.Form form(ReportSession session) {
        return (ReportSession.Form) session.view().get("pendingForm");
    }

    private static ReportRules.Validation validation(ReportSession session) {
        return (ReportRules.Validation) session.view().get("validation");
    }

    private static void assertConflict(Runnable action) {
        assertEquals(409, assertThrows(Problem.class, action::run).status);
    }

    private static Map<String, Object> article() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("date", "2026-09-10");
        report.put("product", "Orbit AI Toolkit");
        report.put("title", "Runtime lessons");
        report.put("type", "article");
        report.put("outcome", "Published an example; longer-term impact is not yet known.");
        report.put("publicUrl", "https://journal.example.org/posts/runtime");
        report.put("followUp", false);
        return report;
    }

    private static Map<String, Object> workshop() {
        Map<String, Object> report = article();
        report.put("type", "workshop");
        report.put("event", "Runtime Days");
        report.put("delivery", "hybrid");
        report.put("duration", 2);
        report.put("onsiteAttendees", 32);
        report.put("remoteAttendees", 18);
        report.put("completedExercises", 41);
        report.put("audience", "public community");
        report.put("shareable", true);
        report.put("materialsUrl", "https://code.example.org/demo/lab");
        report.put("travel", true);
        report.put("tripStatus", "pending");
        report.put("tripDue", "2026-09-18");
        return report;
    }
}
