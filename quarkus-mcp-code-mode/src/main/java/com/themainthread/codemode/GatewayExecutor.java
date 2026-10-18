package com.themainthread.codemode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;

final class GatewayExecutor implements AutoCloseable {
    private static final Set<String> WORKER_ERRORS = Set.of("SYNTAX_ERROR", "SCRIPT_ERROR", "HOST_ERROR",
            "INVALID_JSON", "INVALID_RESULT", "RESULT_LIMIT", "FRAME_LIMIT", "PROTOCOL_ERROR", "WORKER_ERROR");
    private final ExecutionLimits limits;
    private final Catalog catalog;
    private final Policy policy;
    private final Trace trace;
    private final List<String> command;
    private final Map<String, String> environment;
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile Thread running;
    private volatile Object requestId;
    private volatile boolean poisoned;
    private volatile boolean closed;
    private volatile Upstream upstream;

    GatewayExecutor(ExecutionLimits limits, Catalog catalog, Policy policy, Trace trace,
            List<String> command, Map<String, String> environment) throws IOException {
        this.limits = limits;
        this.catalog = catalog;
        this.policy = policy;
        this.trace = trace;
        this.command = List.copyOf(command);
        this.environment = Map.copyOf(environment);
        connect();
    }

    private void connect() throws IOException {
        Upstream connected = new Upstream(command, policy.fixture, environment, limits.toolTimeout(), catalog, trace);
        if (Thread.currentThread().isInterrupted() || closed) {
            connected.close();
            throw new CodeModeFailure("CANCELLED", "Session connection was cancelled.");
        }
        upstream = connected;
        policy.resetDiscovery();
    }

    /** Trusted, explicitly selected fixture setup, unavailable through the code tool. */
    void startFixture(int port) throws Exception {
        var result = upstream.call("quarkus_start", Json.MAPPER.valueToTree(Map.of("projectDir", policy.fixture.toString(),
                "buildTool", "maven", "httpPort", port)));
        if (result.path("isError").asBoolean()) {
            throw new IOException("Fixture setup failed");
        }
        FixtureReady.await(port);
    }

    ExecutionResult execute(Object id, String script) {
        return execute(id, script, () -> false);
    }

    ExecutionResult execute(Object id, String script, java.util.function.BooleanSupplier cancelled) {
        long start = System.nanoTime();
        if (closed || poisoned) {
            return failure("CLEANUP_FAILED", "Gateway unavailable; restart after resolving cleanup.");
        }
        if (!busy.compareAndSet(false, true)) {
            return failure("BUSY", "One script is already executing.");
        }
        running = Thread.currentThread();
        requestId = id;
        Process worker = null;
        Path directory = null;
        var controller = Executors.newVirtualThreadPerTaskExecutor();
        var state = new State();
        JsonNode value = null;
        ExecutionResult.Failure error = null;
        boolean cleaned = true;
        boolean reset = false;
        try {
            if (cancelled.getAsBoolean()) {
                throw new CodeModeFailure("CANCELLED", "Execution was cancelled before dispatch.");
            }
            size(script, limits.scriptBytes(), "SCRIPT_LIMIT");
            directory = Files.createTempDirectory("code-mode-worker-");
            worker = ChildJvm.start(ChildJvm.command(WorkerMain.class.getName()), directory);
            state.workerPid = worker.pid();
            Process owned = worker;
            var pending = controller.submit(() -> {
                if (upstream == null) {
                    connect();
                }
                state.upstreamPid = upstream.transport.process.pid();
                return exchange(owned, script, state);
            });
            try {
                value = pending.get(Math.max(1, limits.executionTimeout().toNanos() - (System.nanoTime() - start)),
                        TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                throw new CodeModeFailure("EXECUTION_TIMEOUT", "Script exceeded the parent-owned wall-clock deadline.");
            } catch (ExecutionException e) {
                if (e.getCause() instanceof CodeModeFailure failure) {
                    throw failure;
                }
                throw new CodeModeFailure("WORKER_EXIT", "Worker or upstream connection ended unexpectedly.");
            } finally {
                pending.cancel(true);
            }
        } catch (CodeModeFailure e) {
            error = new ExecutionResult.Failure(e.kind, e.getMessage());
            reset = !Set.of("SYNTAX_ERROR", "SCRIPT_ERROR", "INVALID_RESULT", "RESULT_LIMIT", "SCRIPT_LIMIT",
                    "INVALID_ARGUMENTS", "INVALID_JSON", "DENIED", "DISCOVERY_REQUIRED", "CALL_LIMIT", "INPUT_LIMIT").contains(e.kind);
        } catch (InterruptedException e) {
            error = new ExecutionResult.Failure("CANCELLED", "Execution was cancelled.");
            reset = true;
            // Cleanup runs without the interrupt flag, then the handler completes normally.
        } catch (IOException e) {
            error = new ExecutionResult.Failure("PROCESS_ERROR", "Could not launch a worker.");
            reset = true;
        } finally {
            Thread.interrupted();
            try {
                ChildJvm.stop(worker);
            } catch (RuntimeException e) {
                cleaned = false;
            }
            if (reset) {
                try {
                    resetUpstream();
                } catch (RuntimeException e) {
                    cleaned = false;
                }
            }
            controller.shutdownNow();
            try {
                if (!controller.awaitTermination(3, TimeUnit.SECONDS)) {
                    cleaned = false;
                }
                if (directory != null) {
                    Files.delete(directory);
                }
            } catch (IOException | InterruptedException e) {
                cleaned = false;
            }
            if (!cleaned) {
                error = new ExecutionResult.Failure("CLEANUP_FAILED", "Owned resources did not terminate; restart required.");
                poisoned = true;
            }
            running = null;
            requestId = null;
            busy.set(false);
        }
        var metrics = new ExecutionResult.Metrics(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), state.calls,
                state.workerPid, state.upstreamPid, state.ready, cleaned);
        trace.write(Map.of("event", "execution", "scriptHash", Catalog.sha(script == null ? "" : script),
                "ok", error == null, "errorKind", error == null ? "" : error.kind(), "metrics", metrics,
                "rawUpstreamBytes", state.rawBytes, "returnedBytes", value == null ? 0 : value.toString().getBytes(StandardCharsets.UTF_8).length));
        return new ExecutionResult(error == null, error == null ? value : null, error, metrics);
    }

    private JsonNode exchange(Process worker, String script, State state) throws IOException {
        Frames.write(worker.getOutputStream(), Map.of("type", "run", "script", catalog.javascript + script,
                "inputJson", Json.MAPPER.valueToTree(Map.of("projectDir", policy.fixture.toString())).toString(),
                "resultBytes", limits.resultBytes()));
        while (true) {
            JsonNode message = Frames.read(worker.getInputStream());
            switch (message.path("type").asText()) {
                case "ready" -> {
                    if (state.ready) {
                        throw new CodeModeFailure("PROTOCOL_ERROR", "Duplicate ready frame.");
                    }
                    state.ready = true;
                }
                case "call" -> {
                    if (!state.ready) {
                        throw new CodeModeFailure("PROTOCOL_ERROR", "Call before worker ready.");
                    }
                    if (++state.calls > limits.calls()) {
                        throw new CodeModeFailure("CALL_LIMIT", "Script exceeded its nested call limit.");
                    }
                    String name = message.path("name").asText();
                    String args = message.path("argumentsJson").asText();
                    long start = System.nanoTime();
                    String outcome = "ok";
                    JsonNode facts = Json.MAPPER.createObjectNode();
                    try {
                        size(args, limits.inputBytes(), "INPUT_LIMIT");
                        JsonNode checked = policy.check(name, Json.parse(args));
                        facts = Policy.argumentFacts(name, checked);
                        JsonNode result = upstream.call(name, checked);
                        outcome = result.path("isError").asBoolean() ? "UPSTREAM_TOOL_ERROR" : "ok";
                        if (name.equals("quarkus_searchTools")) {
                            policy.recordDiscovery(result);
                        }
                        size(result.toString(), limits.upstreamBytes(), "UPSTREAM_LIMIT");
                        state.rawBytes += result.toString().getBytes(StandardCharsets.UTF_8).length;
                        Frames.write(worker.getOutputStream(), Map.of("type", "reply", "resultJson", result.toString()));
                    } catch (CodeModeFailure e) {
                        outcome = e.kind;
                        throw e;
                    } finally {
                        trace.write(Map.of("event", "nested", "tool", catalog.schemas.containsKey(name) ? name : "unknown",
                                "argumentsHash", Catalog.sha(args), "outcome", outcome,
                                "argumentFacts", facts,
                                "durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
                    }
                }
                case "result" -> {
                    if (!state.ready || !message.has("value")) {
                        throw new CodeModeFailure("PROTOCOL_ERROR", "Invalid worker result.");
                    }
                    size(message.get("value").toString(), limits.resultBytes(), "RESULT_LIMIT");
                    return message.get("value");
                }
                case "failure" -> {
                    String kind = message.path("kind").asText();
                    if (!WORKER_ERRORS.contains(kind)) {
                        kind = "PROTOCOL_ERROR";
                    }
                    throw new CodeModeFailure(kind, kind.equals("SYNTAX_ERROR")
                            ? "JavaScript compilation failed; check script syntax." : "QuickJS execution failed (" + kind + ").");
                }
                default -> throw new CodeModeFailure("PROTOCOL_ERROR", "Unexpected worker frame.");
            }
        }
    }

    void cancel(Object id) {
        Thread active = running;
        if (active != null && (id == null || java.util.Objects.equals(requestId, id))) {
            active.interrupt();
        }
    }

    private void resetUpstream() {
        Upstream old = upstream;
        upstream = null;
        if (old != null) {
            old.close();
        }
    }

    @Override
    public void close() {
        closed = true;
        cancel(null);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (busy.get() && System.nanoTime() < deadline) {
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        resetUpstream();
        if (busy.get()) {
            throw new CodeModeFailure("CLEANUP_FAILED", "Execution did not finish during shutdown.");
        }
    }

    static ExecutionResult failure(String kind, String message) {
        return new ExecutionResult(false, null, new ExecutionResult.Failure(kind, message),
                new ExecutionResult.Metrics(0, 0, 0, 0, false, true));
    }

    private static void size(String value, int limit, String kind) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > limit) {
            throw new CodeModeFailure(kind, "UTF-8 input or output exceeds the configured byte limit.");
        }
    }

    private static final class State {
        volatile int calls;
        volatile boolean ready;
        volatile long workerPid;
        volatile long upstreamPid;
        volatile long rawBytes;
    }
}
