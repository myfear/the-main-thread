package dev.mainthread.fieldnotes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;

/** In-memory state for one report. All mutations and event sequence allocation use one lock. */
public final class ReportSession {
    public record Event(long sequence, String runId, Map<String, Object> data) {}
    public record Form(String id, long revision, String question, List<ReportRules.Field> fields, String expiresAt) {}
    private record Subscriber(String runId, MultiEmitter<? super Event> emitter) {}
    private record Answer(String formId, Map<String, Object> values) {}

    final String id = UUID.randomUUID().toString();
    final String mcpToken = UUID.randomUUID().toString();
    private final Clock clock;
    private final YearMonth month;
    private final Duration formTimeout;
    private Map<String, Object> values = new LinkedHashMap<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Subscriber> subscribers = new ArrayList<>();
    private final List<Map<String, Object>> messages = new ArrayList<>();
    private final Map<String, Answer> answers = new LinkedHashMap<>();
    private final Set<String> usedRuns = new java.util.HashSet<>();
    private long revision;
    private long sequence;
    private String runId;
    private String messageId;
    private boolean running;
    private boolean saved;
    private boolean closed;
    private Form form;
    private CompletableFuture<Map<String, Object>> formResult;
    private List<String> cleared = List.of();

    public ReportSession(YearMonth month, String author, Clock clock, Duration formTimeout) {
        this.month = month;
        this.clock = clock;
        this.formTimeout = formTimeout;
        Object normalizedAuthor = ReportRules.normalize("author", author);
        if (normalizedAuthor == null) throw new IllegalArgumentException("Choose an advocate");
        values.put("author", normalizedAuthor);
    }

    public synchronized Map<String, Object> view() {
        ReportRules.Validation validation = ReportRules.validate(values, month, LocalDate.now(clock));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", id);
        view.put("month", month.toString());
        view.put("today", LocalDate.now(clock).toString());
        view.put("revision", revision);
        view.put("values", new LinkedHashMap<>(values));
        view.put("catalog", ReportRules.catalog());
        view.put("validation", new ReportRules.Validation(validation.ready() && form == null, validation.issues(), validation.required()));
        view.put("totalAttendees", ReportRules.attendance(values));
        view.put("metricsStatus", "collect later".equals(values.get("metrics")) ? "pending collection (demo only)" : null);
        view.put("status", saved ? "saved" : validation.ready() && form == null ? "ready" : "incomplete");
        view.put("pendingForm", form);
        view.put("running", running);
        view.put("runId", runId);
        view.put("cursor", sequence);
        view.put("clearedFields", cleared);
        view.put("messages", messages.stream().map(LinkedHashMap::new).toList());
        return view;
    }

    public synchronized Map<String, Object> update(long expectedRevision, Map<String, Object> patch) {
        mutable(expectedRevision);
        if (form != null) throw new Problem(409, "Answer or cancel the current form first");
        apply(patch);
        publishState();
        return view();
    }

    private void apply(Map<String, Object> patch) {
        if (patch == null || patch.isEmpty() || patch.size() > 40) throw new IllegalArgumentException("Supply 1–40 report fields");
        Map<String, Object> next = new LinkedHashMap<>(values);
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            Object value = ReportRules.normalize(entry.getKey(), entry.getValue());
            if (value == null) next.remove(entry.getKey()); else next.put(entry.getKey(), value);
        }
        // Remove dependent values repeatedly: clearing a selector can deactivate another field.
        List<String> removed = new ArrayList<>();
        boolean changed;
        do {
            Set<String> applicable = ReportRules.required(next);
            List<String> inactive = next.keySet().stream().filter(key -> !applicable.contains(key)).toList();
            changed = !inactive.isEmpty();
            for (String key : inactive) { next.remove(key); removed.add(key); }
        } while (changed);
        values = next;
        cleared = List.copyOf(removed);
        revision++;
    }

    public synchronized CompletableFuture<Map<String, Object>> showForm(long expectedRevision, List<String> fieldIds, String question) {
        mutable(expectedRevision);
        if (!running) throw new Problem(409, "Start an assistant run first");
        if (form != null) throw new Problem(409, "There is already a pending form");
        if (fieldIds == null || fieldIds.isEmpty() || fieldIds.size() > 4 || Set.copyOf(fieldIds).size() != fieldIds.size()) throw new IllegalArgumentException("Choose 1–4 different fields");
        if (question == null || question.isBlank() || question.length() > 1000) throw new IllegalArgumentException("Supply a short question");
        if (!ReportRules.required(values).containsAll(fieldIds)) throw new IllegalArgumentException("Choose currently applicable fields from getReport");
        form = new Form(UUID.randomUUID().toString(), revision, question, fieldIds.stream().map(ReportRules::field).toList(), Instant.now(clock).plus(formTimeout).toString());
        formResult = new CompletableFuture<>();
        String formId = form.id();
        CompletableFuture.delayedExecutor(formTimeout.toMillis(), TimeUnit.MILLISECONDS).execute(() -> expire(formId));
        publishState();
        return formResult;
    }

    private synchronized void expire(String formId) {
        if (form != null && form.id().equals(formId)) cancelForm("expired");
    }

    public synchronized Map<String, Object> answer(String formId, long expectedRevision, Map<String, Object> submitted) {
        if (answers.containsKey(formId)) {
            if (!answers.get(formId).values().equals(submitted)) throw new Problem(409, "This form already has a different answer");
            return view();
        }
        mutable(expectedRevision);
        if (form == null || !form.id().equals(formId)) throw new Problem(409, "This form is no longer active");
        if (Instant.now(clock).isAfter(Instant.parse(form.expiresAt()))) { cancelForm("expired"); throw new Problem(409, "This form expired"); }
        Set<String> requested = form.fields().stream().map(ReportRules.Field::id).collect(java.util.stream.Collectors.toSet());
        if (submitted == null || !requested.equals(submitted.keySet())) throw new IllegalArgumentException("Answer exactly the fields shown in this form");
        for (String key : requested) if (ReportRules.normalize(key, submitted.get(key)) == null) throw new IllegalArgumentException(ReportRules.field(key).label() + " needs an answer");
        apply(submitted);
        answers.put(formId, new Answer(formId, Map.copyOf(submitted)));
        CompletableFuture<Map<String, Object>> result = formResult;
        form = null;
        formResult = null;
        publishState();
        Map<String, Object> snapshot = view();
        result.complete(Map.of("status", "submitted", "report", snapshot));
        return snapshot;
    }

    public synchronized void cancelForm(String reason) {
        if (form == null) return;
        CompletableFuture<Map<String, Object>> result = formResult;
        form = null;
        formResult = null;
        publishState();
        result.complete(Map.of("status", reason, "report", view(), "instruction", "Stop asking. Summarize remaining requirements and finish this turn."));
    }

    public synchronized Map<String, Object> save(long expectedRevision) {
        if (saved && revision == expectedRevision) return view();
        mutable(expectedRevision);
        if (running || form != null || !ReportRules.validate(values, month, LocalDate.now(clock)).ready()) throw new Problem(409, "The report must be complete and the assistant finished before saving");
        saved = true;
        publishState();
        return view();
    }

    private void mutable(long expectedRevision) {
        if (closed || saved) throw new Problem(409, "This report is closed or saved");
        if (revision != expectedRevision) throw new Problem(409, "The report changed. Refresh it before continuing");
    }

    public synchronized void beginRun(String requestedRun, String prompt) {
        if (closed || saved || running) throw new Problem(409, "Finish or stop the current report run first");
        if (usedRuns.size() >= 100) throw new Problem(409, "Start a new report after 100 turns");
        if (!usedRuns.add(requestedRun)) throw new Problem(409, "This run already exists; reconnect instead of resubmitting");
        runId = requestedRun;
        messageId = null;
        running = true;
        messages.add(new LinkedHashMap<>(Map.of("id", UUID.randomUUID().toString(), "role", "user", "content", prompt)));
        emit(Map.of("type", "RUN_STARTED", "threadId", id, "runId", runId));
        publishState();
    }

    public synchronized void text(String expectedRun, String delta) {
        if (!running || !expectedRun.equals(runId) || delta.isEmpty()) return;
        if (messageId == null) {
            messageId = UUID.randomUUID().toString();
            messages.add(new LinkedHashMap<>(Map.of("id", messageId, "role", "assistant", "content", "")));
            emit(Map.of("type", "TEXT_MESSAGE_START", "messageId", messageId, "role", "assistant"));
        }
        Map<String, Object> message = messages.get(messages.size() - 1);
        message.put("content", message.get("content").toString() + delta);
        emit(Map.of("type", "TEXT_MESSAGE_CONTENT", "messageId", messageId, "delta", delta));
    }

    public synchronized void finish(String expectedRun, String error) {
        if (!running || !expectedRun.equals(runId)) return;
        running = false;
        cancelForm(error == null ? "cancelled" : "failed");
        if (messageId != null) emit(Map.of("type", "TEXT_MESSAGE_END", "messageId", messageId));
        publishState();
        emit(error == null ? Map.of("type", "RUN_FINISHED", "threadId", id, "runId", runId)
                : Map.of("type", "RUN_ERROR", "message", error, "code", "BOB_RUN_FAILED"));
        for (Subscriber subscriber : List.copyOf(subscribers)) subscriber.emitter().complete();
        subscribers.clear();
    }

    public synchronized void close() {
        closed = true;
        if (running) finish(runId, "Report reset"); else cancelForm("cancelled");
        List.copyOf(subscribers).forEach(s -> s.emitter().complete());
        subscribers.clear();
    }

    public synchronized String activeRun() { return running ? runId : null; }
    public synchronized long cursor() { return sequence; }

    public Multi<Event> stream(long after, String forRun) {
        return Multi.createFrom().emitter(emitter -> {
            synchronized (this) {
                if (after < 0 || after > sequence) { emitter.fail(new Problem(400, "Invalid event cursor")); return; }
                if (!events.isEmpty() && after < events.get(0).sequence() - 1) { emitter.fail(new Problem(409, "Event history expired; reload the report snapshot")); return; }
                for (Event event : events) if (event.sequence() > after && forRun.equals(event.runId())) emitter.emit(event);
                if (running && forRun.equals(runId)) {
                    Subscriber subscriber = new Subscriber(forRun, emitter);
                    subscribers.add(subscriber);
                    emitter.onTermination(() -> { synchronized (this) { subscribers.remove(subscriber); } });
                } else emitter.complete();
            }
        });
    }

    private void publishState() { emit(Map.of("type", "STATE_SNAPSHOT", "snapshot", view())); }
    private void emit(Map<String, Object> data) {
        Event event = new Event(++sequence, runId, data);
        events.add(event);
        if (events.size() > 6000) events.remove(0);
        for (Subscriber s : List.copyOf(subscribers)) if (java.util.Objects.equals(s.runId(), runId)) s.emitter().emit(event);
    }
}
