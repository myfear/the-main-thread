package com.themainthread.codemode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpSchema;

/** Replays a direct client sequence and one handwritten script; this is not an agent evaluation. */
public final class ComparisonClient {
    private ComparisonClient() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toRealPath();
        Path project = Path.of(System.getenv("PROJECT_DIR")).toRealPath();
        int port = Integer.parseInt(System.getenv().getOrDefault("DEMO_PORT", "18191"));
        var directCalls = new ArrayList<JsonNode>();
        JsonNode direct;
        var command = List.of(System.getenv("JBANG_BIN"), "io.quarkus:quarkus-agent-mcp:1.0.10:runner");
        try (var upstream = new Upstream(command, project, GatewayMain.upstreamEnvironment(), Duration.ofSeconds(90),
                Catalog.pinned(), new Trace(root.resolve(".local/comparison-direct")))) {
            JsonNode setup = upstream.call("quarkus_start", Json.MAPPER.valueToTree(Map.of("projectDir", project.toString(),
                    "buildTool", "maven", "httpPort", port)));
            text(setup);
            FixtureReady.await(port);
            JsonNode requests = Json.parse(Files.readString(root.resolve("examples/investigate-direct.json")));
            var results = new ArrayList<JsonNode>();
            for (JsonNode template : requests) {
                ObjectNode request = template.deepCopy();
                ((ObjectNode) request.path("arguments")).put("projectDir", project.toString());
                directCalls.add(request);
                results.add(upstream.call(request.path("name").asText(), request.path("arguments")));
                text(results.getLast());
            }
            String status = text(results.get(0));
            List<String> errors = text(results.get(1)).lines().filter(line -> line.contains("DEMO_ERROR payment")).toList();
            JsonNode diagnostic = Json.parse(text(results.get(3)));
            if (!diagnostic.path("hasException").isBoolean()) throw new IllegalStateException("Invalid diagnostic");
            direct = Json.MAPPER.valueToTree(Map.of("status", status, "errors", errors,
                    "hasException", diagnostic.path("hasException").asBoolean()));
        }

        // The direct session has stopped its demo. Start a fresh session with the same sample application.
        var transport = new OwnedTransport(List.of(root.resolve("scripts/start-mcp.sh").toString()), root,
                System.getenv(), 262_144);
        var client = McpClient.sync(transport).initializationTimeout(Duration.ofSeconds(180))
                .requestTimeout(Duration.ofSeconds(90)).build();
        try {
            client.initialize();
            String script = Files.readString(root.resolve("examples/investigate.js"));
            var response = client.callTool(new McpSchema.CallToolRequest("code", Map.of("script", script)));
            JsonNode envelope = Json.MAPPER.valueToTree(response.structuredContent());
            if (!envelope.path("ok").asBoolean()) throw new IllegalStateException(envelope.toString());
            JsonNode expected = Json.MAPPER.valueToTree(Map.of("status", "running (port: " + port + ")",
                    "errors", List.of("DEMO_ERROR payment retry exhausted for request pay-1"), "hasException", false));
            if (!direct.equals(expected) || !direct.equals(envelope.path("value"))) {
                throw new IllegalStateException("Direct and code-mode results must equal the demo's expected finding");
            }
            if (envelope.path("metrics").path("nestedCalls").asInt() != directCalls.size()) {
                throw new IllegalStateException("Expected the same upstream call count");
            }
            long worker = envelope.path("metrics").path("workerPid").asLong();
            if (worker <= 0 || ProcessHandle.of(worker).map(ProcessHandle::isAlive).orElse(false)) {
                throw new IllegalStateException("Worker was not reaped");
            }
            // Machine-readable demonstration output, including exactly what each client submitted.
            System.out.println(Json.MAPPER.valueToTree(Map.of(
                    "direct", Map.of("requests", directCalls, "finding", direct),
                    "code", Map.of("requests", List.of(Map.of("name", "code", "arguments", Map.of("script", script))),
                            "upstreamCalls", envelope.path("metrics").path("nestedCalls"), "finding", envelope.path("value")),
                    "equal", true)).toPrettyString());
        } finally {
            transport.process.getOutputStream().close();
            boolean exited = transport.process.waitFor(15, TimeUnit.SECONDS);
            transport.stop();
            client.closeGracefully();
            if (!exited) throw new IllegalStateException("Gateway did not exit on stdin EOF");
        }
    }

    private static String text(JsonNode result) {
        if (result.path("isError").asBoolean()) throw new IllegalStateException("Upstream operation failed");
        var parts = new ArrayList<String>();
        for (JsonNode block : result.path("content")) {
            if (block.path("type").asText().equals("text")) parts.add(block.path("text").asText());
        }
        return String.join("\n", parts);
    }
}
