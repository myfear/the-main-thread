package dev.mainthread.fieldnotes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.agentclientprotocol.sdk.client.transport.AgentParameters;
import io.smallrye.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;

/** SmallRye owns process/stdio transport; this adapter maps the ACP JSON-RPC lifecycle. */
final class BobAgent implements AutoCloseable {
    private final StdioAcpClientTransport transport;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonNode>> requests = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final Path workspace;
    private final BobConfig config;
    private volatile Consumer<String> text = ignored -> {};
    private String sessionId;
    private volatile boolean closed;

    BobAgent(BobConfig config, String token) throws Exception {
        this.config = config;
        workspace = Files.createTempDirectory("field-notes-bob-");
        AgentParameters.Builder parameters = AgentParameters.builder(config.binary());
        for (String argument : List.of("acp", "--trust", "--accept-license", "--disable-subagents", "--log-level", "silent")) parameters.arg(argument);
        String key = config.apiKey().orElse("");
        if (key.isBlank() && config.keyFile().isPresent()) {
            JsonNode credential = new ObjectMapper().readTree(Files.readString(Path.of(config.keyFile().get())));
            key = credential.path("apikey").asText();
            if (key.isBlank()) throw new IllegalArgumentException("Credential file has no apikey field");
        }
        if (!key.isBlank()) {
            parameters.addEnvVar("BOBSHELL_API_KEY", key);
            parameters.addEnvVar("BOB_API_KEY", key);
        }
        transport = new StdioAcpClientTransport(parameters.build());
        mapper = transport.getMapper();
        transport.setStdErrorHandler(ignored -> {});
        transport.setInboundMessageHandler(this::receive);
        try {
            transport.connect();
            JsonNode init = request("initialize", Map.of("protocolVersion", 1, "clientCapabilities", Map.of(),
                    "clientInfo", Map.of("name", "field-notes", "version", "1.0.0")), Duration.ofSeconds(40)).get();
            if (!init.path("agentCapabilities").path("mcpCapabilities").path("http").asBoolean()) throw new IllegalStateException("This Bob version does not advertise MCP HTTP support");
            Map<String, Object> mcp = Map.of("type", "http", "name", "field-notes", "url", config.callbackUrl() + "/mcp", "headers", List.of(Map.of("name", "X-Report-Token", "value", token)));
            JsonNode session = request("session/new", Map.of("cwd", workspace.toString(), "mcpServers", List.of(mcp)), Duration.ofSeconds(60)).get();
            sessionId = session.path("sessionId").asText();
            if (sessionId.isBlank()) throw new IllegalStateException("Bob did not create a session");
        } catch (Exception failure) { close(); throw failure; }
    }

    CompletableFuture<JsonNode> prompt(String prompt, Consumer<String> onText) {
        text = onText;
        return request("session/prompt", Map.of("sessionId", sessionId,
                "prompt", List.of(Map.of("type", "text", "text", prompt))), config.promptTimeout());
    }

    private CompletableFuture<JsonNode> request(String method, Object params, Duration timeout) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Bob connection closed"));
        int id = ids.incrementAndGet();
        CompletableFuture<JsonNode> result = new CompletableFuture<>();
        requests.put(id, result);
        ObjectNode message = mapper.createObjectNode();
        message.put("jsonrpc", "2.0"); message.put("id", id); message.put("method", method);
        message.set("params", mapper.valueToTree(params));
        try { transport.sendMessage(message); }
        catch (RuntimeException e) { result.completeExceptionally(e); }
        result.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).whenComplete((v, e) -> requests.remove(id));
        return result;
    }

    private void receive(JsonNode message) {
        if (message.has("id") && message.has("method")) {
            permission(message);
        } else if (message.has("id")) {
            CompletableFuture<JsonNode> response = requests.remove(message.path("id").asInt());
            if (response == null) return;
            if (message.has("error")) response.completeExceptionally(new IllegalStateException("Bob rejected an ACP request"));
            else response.complete(message.path("result"));
        } else if ("session/update".equals(message.path("method").asText())) {
            JsonNode update = message.path("params").path("update");
            if ("agent_message_chunk".equals(update.path("sessionUpdate").asText())) text.accept(update.path("content").path("text").asText());
        }
    }

    private void permission(JsonNode message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0"); response.set("id", message.get("id"));
        if (!"session/request_permission".equals(message.path("method").asText())) {
            response.set("error", mapper.valueToTree(Map.of("code", -32601, "message", "Unsupported client capability")));
        } else {
            // Only the three reporting tools can be approved; shell and filesystem calls fail closed.
            JsonNode call = message.path("params").path("toolCall");
            String title = call.path("title").asText();
            boolean reporting = List.of("Running GetReport (field-notes)",
                    "Running UpdateReport (field-notes)", "Running ShowForm (field-notes)").contains(title);
            String choice = null;
            for (JsonNode option : message.path("params").path("options")) {
                if ((reporting ? "allow_once" : "reject_once").equals(option.path("kind").asText())) choice = option.path("optionId").asText();
            }
            response.set("result", mapper.valueToTree(Map.of("outcome", choice == null ? Map.of("outcome", "cancelled") : Map.of("outcome", "selected", "optionId", choice))));
        }
        transport.sendMessage(response);
    }

    @Override
    public void close() {
        closed = true;
        requests.values().forEach(f -> f.completeExceptionally(new IllegalStateException("Bob connection closed")));
        requests.clear();
        if (transport != null) transport.closeGracefully();
        // Bob may create workspace metadata; only remove the directory when it is empty.
        try { Files.deleteIfExists(workspace); } catch (java.io.IOException ignored) { }
    }
}
