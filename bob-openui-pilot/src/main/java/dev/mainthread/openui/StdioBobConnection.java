package dev.mainthread.openui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.agentclientprotocol.sdk.client.transport.AgentParameters;
import io.smallrye.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;

/** SmallRye owns process and stdio transport; this class maps the ACP session lifecycle. */
final class StdioBobConnection implements BobConnection {
    private final StdioAcpClientTransport transport;
    private final ObjectMapper mapper;
    private final BobConfig config;
    private final Path workspace;
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Consumer<String> onText = ignored -> { };
    private String sessionId;

    StdioBobConnection(BobConfig config, String key) throws IOException {
        this.config = config;
        workspace = Files.createTempDirectory("bob-openui-");
        AgentParameters parameters = AgentParameters.builder(config.binary())
                .args("acp", "--trust", "--accept-license", "--disable-mcp", "--disable-subagents", "--log-level", "silent")
                .addEnvVar("BOBSHELL_API_KEY", key)
                .addEnvVar("BOB_API_KEY", key)
                .build();
        transport = new StdioAcpClientTransport(parameters);
        mapper = transport.getMapper();
        transport.setStdErrorHandler(ignored -> { });
        transport.setInboundMessageHandler(this::receive);
    }

    @Override
    public String initialize() throws Exception {
        synchronized (transport) {
            if (closed.get()) {
                throw new IllegalStateException("Bob connection is closed");
            }
            transport.connect();
        }
        JsonNode initialized = request("initialize", Map.of("protocolVersion", 1,
                "clientCapabilities", Map.of(), "clientInfo", Map.of("name", "bob-openui-pilot", "version", "1.0.0")),
                Duration.ofSeconds(30)).get();
        JsonNode session = request("session/new", Map.of("cwd", workspace.toString(), "mcpServers", List.of(),
                "_meta", Map.of("bob/mode", "ask")), Duration.ofSeconds(30)).get();
        sessionId = session.path("sessionId").asText();
        if (sessionId.isBlank()) {
            throw new IllegalStateException("Bob did not return an ACP session ID");
        }
        return initialized.path("agentInfo").path("version").asText("unknown");
    }

    @Override
    public String prompt(String instructions, Consumer<String> text) throws Exception {
        onText = text;
        JsonNode result = request("session/prompt", Map.of("sessionId", sessionId,
                "prompt", List.of(Map.of("type", "text", "text", instructions))), config.promptTimeout()).get();
        return result.path("stopReason").asText("unknown");
    }

    private CompletableFuture<JsonNode> request(String method, Object params, Duration timeout) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Bob connection is closed"));
        }
        int id = ids.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        ObjectNode message = mapper.createObjectNode().put("jsonrpc", "2.0").put("id", id).put("method", method);
        message.set("params", mapper.valueToTree(params));
        try {
            transport.sendMessage(message);
        } catch (RuntimeException failure) {
            future.completeExceptionally(failure);
        }
        future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).whenComplete((result, failure) -> pending.remove(id));
        return future;
    }

    private void receive(JsonNode message) {
        if (message.has("method") && message.has("id")) {
            transport.sendMessage(clientResponse(mapper, message));
        } else if (message.has("id")) {
            CompletableFuture<JsonNode> response = pending.remove(message.path("id").asInt());
            if (response != null) {
                if (message.has("error")) {
                    response.completeExceptionally(new IllegalStateException("Bob rejected an ACP request (code "
                            + message.path("error").path("code").asInt() + ")"));
                } else {
                    response.complete(message.path("result"));
                }
            }
        } else if ("session/update".equals(message.path("method").asText())) {
            JsonNode update = message.path("params").path("update");
            if ("agent_message_chunk".equals(update.path("sessionUpdate").asText())
                    && "text".equals(update.path("content").path("type").asText())) {
                onText.accept(update.path("content").path("text").asText());
            }
        }
    }

    static ObjectNode clientResponse(ObjectMapper mapper, JsonNode request) {
        ObjectNode response = mapper.createObjectNode().put("jsonrpc", "2.0");
        response.set("id", request.get("id"));
        if ("session/request_permission".equals(request.path("method").asText())) {
            // Generated presentation never grants agent tool permissions.
            response.set("result", mapper.valueToTree(Map.of("outcome", Map.of("outcome", "cancelled"))));
        } else {
            response.set("error", mapper.valueToTree(Map.of("code", -32601, "message", "Client capability not supported")));
        }
        return response;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        pending.values().forEach(future -> future.completeExceptionally(new IllegalStateException("Bob connection closed")));
        pending.clear();
        synchronized (transport) {
            transport.closeGracefully();
        }
        try {
            Files.deleteIfExists(workspace);
        } catch (IOException ignored) {
            // Preserve any Bob-created workspace metadata; never recursively delete a workspace.
        }
    }
}
