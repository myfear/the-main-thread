package com.themainthread.codemode;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

/** MCP SDK transport with bounded wire frames, no message queue, and explicit process ownership. */
final class OwnedTransport implements McpClientTransport {
    static final JacksonMcpJsonMapper MAPPER = new JacksonMcpJsonMapper(Json.MAPPER);
    static final List<String> PROTOCOLS = List.of("2024-11-05", "2025-03-26");
    final Process process;
    private final int maximum;
    private volatile boolean closed;
    private volatile Consumer<Throwable> exceptionHandler = ignored -> { };
    private Thread reader;
    volatile JsonNode lastResult;
    volatile Object lastRequestId;
    volatile String fault;
    volatile boolean catalogChanged;

    OwnedTransport(List<String> command, Path directory, Map<String, String> environment, int maximum) throws IOException {
        this.maximum = maximum;
        var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear();
        builder.environment().putAll(environment);
        process = builder.start();
    }

    @Override
    public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        return Mono.fromRunnable(() -> reader = Thread.ofVirtual().name("owned-mcp-reader").start(() -> {
            try {
                int notifications = 0;
                while (!closed) {
                    JsonNode wire = JsonLines.read(process.getInputStream(), maximum);
                    if (wire.has("result")) {
                        if (java.util.Objects.equals(lastRequestId, Json.MAPPER.convertValue(wire.get("id"), Object.class))) {
                            lastResult = wire.get("result");
                        }
                        notifications = 0;
                    } else if (++notifications > 100) {
                        throw new CodeModeFailure("UPSTREAM_PROTOCOL", "Upstream notification limit exceeded.");
                    }
                    if (wire.path("method").asText().equals("notifications/tools/list_changed")) {
                        catalogChanged = true;
                    }
                    var message = McpSchema.deserializeJsonRpcMessage(MAPPER, wire.toString());
                    handler.apply(Mono.just(message)).block(Duration.ofSeconds(5));
                }
            } catch (Exception e) {
                if (!closed) {
                    fault = e instanceof CodeModeFailure failure ? failure.kind : "UPSTREAM_EXIT";
                    exceptionHandler.accept(new IllegalStateException(fault));
                }
            }
        }));
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        return Mono.fromRunnable(() -> {
            try {
                if (closed || fault != null) {
                    throw new IOException();
                }
                byte[] bytes = Json.bytes(message);
                if (message instanceof McpSchema.JSONRPCRequest request) {
                    lastRequestId = request.id();
                }
                if (bytes.length > maximum) {
                    throw new IOException();
                }
                synchronized (process.getOutputStream()) {
                    process.getOutputStream().write(bytes);
                    process.getOutputStream().write('\n');
                    process.getOutputStream().flush();
                }
            } catch (IOException e) {
                throw new CodeModeFailure("UPSTREAM_EXIT", "Upstream connection failed.");
            }
        });
    }

    @Override
    public void setExceptionHandler(Consumer<Throwable> handler) {
        exceptionHandler = handler;
    }

    @Override
    public List<String> protocolVersions() {
        return PROTOCOLS;
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> type) {
        return MAPPER.convertValue(data, type);
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(this::stop);
    }

    synchronized void stop() {
        if (closed) {
            return;
        }
        closed = true;
        ChildJvm.stop(process);
        if (reader != null && reader != Thread.currentThread()) {
            try {
                reader.join(TimeUnit.SECONDS.toMillis(2));
                if (reader.isAlive()) {
                    throw new CodeModeFailure("CLEANUP_FAILED", "Upstream reader did not terminate.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CodeModeFailure("CLEANUP_FAILED", "Interrupted during transport cleanup.");
            }
        }
    }
}
