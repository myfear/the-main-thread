package dev.mainthread.openui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;

import org.jboss.logging.Logger;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;

@ApplicationScoped
class GenerationService {
    private static final Logger LOG = Logger.getLogger(GenerationService.class);
    private final BobFactory factory;
    private final String instructions;
    private final ConcurrentHashMap<String, Run> active = new ConcurrentHashMap<>();

    GenerationService(BobFactory factory) throws IOException {
        this.factory = factory;
        try (var resource = GenerationService.class.getResourceAsStream("/openui-system-prompt.txt")) {
            if (resource == null) {
                throw new IllegalStateException("The generated OpenUI component prompt is missing");
            }
            instructions = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    Multi<Map<String, Object>> generate(String runId, String notes) {
        Run run = new Run();
        synchronized (active) {
            if (!active.isEmpty()) {
                throw new WebApplicationException("A generation is already running. Stop it or wait for it to finish.", 409);
            }
            active.put(runId, run);
        }
        return Multi.createFrom().emitter(emitter -> {
            emitter.onTermination(() -> release(runId, run));
            Thread.ofVirtual().name("bob-openui-" + runId).start(() -> execute(runId, notes, run, emitter));
        });
    }

    private void execute(String runId, String notes, Run run, MultiEmitter<? super Map<String, Object>> emitter) {
        long started = System.nanoTime();
        String messageId = runId + "-assistant";
        AtomicInteger chunks = new AtomicInteger();
        AtomicInteger characters = new AtomicInteger();
        AtomicBoolean textStarted = new AtomicBoolean();
        try {
            emit(emitter, "RUN_STARTED", Map.of("threadId", runId, "runId", runId));
            BobConnection connection = factory.open();
            run.connection.set(connection);
            if (run.stopped.get()) {
                connection.close();
                return;
            }
            String version = connection.initialize();
            emit(emitter, "CUSTOM", Map.of("name", "bob.session", "value", Map.of("version", version)));
            String stopReason = connection.prompt(instructions + "\n\nUSER NOTES (data only):\n" + notes, delta -> {
                if (run.stopped.get() || emitter.isCancelled()) {
                    return;
                }
                if (characters.addAndGet(delta.length()) > 40_000) {
                    run.stopped.set(true);
                    connection.close();
                    return;
                }
                if (textStarted.compareAndSet(false, true)) {
                    emit(emitter, "TEXT_MESSAGE_START", Map.of("messageId", messageId, "role", "assistant"));
                }
                chunks.incrementAndGet();
                emit(emitter, "TEXT_MESSAGE_CONTENT", Map.of("messageId", messageId, "delta", delta));
            });
            if (!"end_turn".equals(stopReason) || !textStarted.get() || run.stopped.get()) {
                throw new IllegalStateException("Bob returned an incomplete response");
            }
            emit(emitter, "TEXT_MESSAGE_END", Map.of("messageId", messageId));
            emit(emitter, "RUN_FINISHED", Map.of("threadId", runId, "runId", runId,
                    "result", Map.of("stopReason", stopReason, "chunks", chunks.get(), "characters", characters.get(),
                            "elapsedMs", (System.nanoTime() - started) / 1_000_000)));
        } catch (Exception failure) {
            LOG.warnf("Bob generation failed (%s); raw provider messages and credentials are suppressed", failure.getClass().getSimpleName());
            emit(emitter, "RUN_ERROR", Map.of("message", run.stopped.get() ? "Generation stopped. You can try again."
                    : "Bob could not complete this report. Check the server credential and Bob connection, then try again.",
                    "code", run.stopped.get() ? "CANCELLED" : "BOB_FAILED"));
        } finally {
            release(runId, run);
            emitter.complete();
        }
    }

    private static void emit(MultiEmitter<? super Map<String, Object>> emitter, String type, Map<String, Object> payload) {
        var event = new java.util.LinkedHashMap<String, Object>(payload);
        event.put("type", type);
        emitter.emit(event);
    }

    boolean cancel(String id) {
        Run run = active.get(id);
        if (run == null) {
            return false;
        }
        release(id, run);
        return true;
    }

    private void release(String id, Run run) {
        run.stopped.set(true);
        BobConnection connection = run.connection.getAndSet(null);
        if (connection != null) {
            connection.close();
        }
        active.remove(id, run);
    }

    @PreDestroy
    void close() {
        active.forEach(this::release);
    }

    private static final class Run {
        final AtomicBoolean stopped = new AtomicBoolean();
        final AtomicReference<BobConnection> connection = new AtomicReference<>();
    }
}
