package com.themainthread.codemode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class Policy {
    static final Set<String> OUTER = Set.of("quarkus_status", "quarkus_logs", "quarkus_skills",
            "quarkus_searchDocs", "quarkus_searchTools", "quarkus_callTool");
    static final Set<String> NESTED = Set.of("devui-exceptions_getLastException");
    final Path fixture;
    private final Catalog catalog;
    private final JsonNode auditedNested;
    private JsonNode discoveredNested;

    Policy(Path fixture, Catalog catalog) throws IOException {
        this.fixture = fixture.toRealPath();
        this.catalog = catalog;
        try (var source = Policy.class.getResourceAsStream("/dev-mcp-diagnostic.json")) {
            auditedNested = Json.MAPPER.readTree(source);
        }
    }

    synchronized JsonNode check(String name, JsonNode args) {
        if (!OUTER.contains(name)) {
            throw new CodeModeFailure("DENIED", "Operation is outside the read-only outer allowlist.");
        }
        catalog.validate(name, args);
        // Additional upstream fields retain their schema semantics. Any path-bearing field is still confined.
        inspectPaths(args);
        var checked = (ObjectNode) args.deepCopy();
        if (args.has("projectDir")) {
            checked.put("projectDir", fixture.toString());
        } else if (name.equals("quarkus_searchDocs")) {
            checked.put("projectDir", fixture.toString());
        }
        if (name.equals("quarkus_callTool")) {
            if (!NESTED.contains(args.path("toolName").asText())) {
                throw new CodeModeFailure("DENIED", "Nested Dev MCP operation is outside the read-only allowlist.");
            }
            JsonNode nested = Json.parse(args.path("toolArguments").asText("{}"));
            if (discoveredNested == null) {
                throw new CodeModeFailure("DISCOVERY_REQUIRED", "Discover this session's Dev MCP diagnostic with quarkus_searchTools first.");
            }
            Catalog.validateValue(discoveredNested, nested);
            if (nested.size() != 1 || !Set.of("1", "2", "3", "4", "5").contains(nested.path("maxCauseDepth").asText())) {
                throw new CodeModeFailure("INVALID_ARGUMENTS", "The diagnostic permits only maxCauseDepth, a string from '1' to '5'.");
            }
            checked.put("toolArguments", nested.toString());
        }
        if (args.has("lines") && (args.path("lines").asInt() < 1 || args.path("lines").asInt() > 200)) {
            throw new CodeModeFailure("DENIED", "Read-only log requests permit 1 to 200 lines.");
        }
        if (args.has("maxResults") && (args.path("maxResults").asInt() < 1 || args.path("maxResults").asInt() > 10)) {
            throw new CodeModeFailure("DENIED", "Documentation requests permit 1 to 10 results.");
        }
        return checked;
    }

    synchronized void resetDiscovery() {
        discoveredNested = null;
    }

    static JsonNode argumentFacts(String name, JsonNode args) {
        var facts = Json.MAPPER.createObjectNode();
        facts.put("projectScoped", args.has("projectDir"));
        if (name.equals("quarkus_callTool")) {
            facts.put("nestedTool", args.path("toolName").asText());
            facts.set("maxCauseDepth", Json.parse(args.path("toolArguments").asText("{}")).path("maxCauseDepth"));
        }
        if (name.equals("quarkus_searchDocs")) {
            facts.put("queryHash", Catalog.sha(args.path("query").asText()));
            facts.set("maxResults", args.path("maxResults"));
        }
        return facts;
    }

    synchronized void recordDiscovery(JsonNode result) {
        if (result.path("isError").asBoolean()) {
            return;
        }
        for (JsonNode block : result.path("content")) {
            if (!block.path("type").asText().equals("text")) {
                continue;
            }
            JsonNode tools;
            try {
                tools = Json.parse(block.path("text").asText());
            } catch (CodeModeFailure e) {
                continue;
            }
            if (!tools.isArray()) {
                continue;
            }
            for (JsonNode tool : tools) {
                if (NESTED.contains(tool.path("name").asText())) {
                    if (!Catalog.canonical(tool.path("inputSchema")).equals(Catalog.canonical(auditedNested.path("inputSchema")))) {
                        throw new CodeModeFailure("CATALOG_CHANGED", "Discovered diagnostic schema differs from the audited schema.");
                    }
                    discoveredNested = tool.path("inputSchema").deepCopy();
                }
            }
        }
    }

    private void inspectPaths(JsonNode value) {
        if (value.isObject()) {
            value.fields().forEachRemaining(e -> {
                String key = e.getKey().toLowerCase(java.util.Locale.ROOT);
                if (key.endsWith("dir") || key.endsWith("path") || key.endsWith("file")) {
                    checkPath(e.getValue());
                } else {
                    inspectPaths(e.getValue());
                }
            });
        } else if (value.isArray()) {
            value.forEach(this::inspectPaths);
        }
    }

    private void checkPath(JsonNode value) {
        try {
            if (!value.isTextual()) {
                throw new IOException();
            }
            Path path = Path.of(value.asText());
            for (Path part : path) {
                if (part.toString().equals("..")) {
                    throw new IOException();
                }
            }
            if (!path.isAbsolute() || !path.toRealPath().equals(fixture)) {
                throw new IOException();
            }
        } catch (IOException | RuntimeException e) {
            throw new CodeModeFailure("DENIED", "Paths must resolve to the active fixture directory without parent traversal.");
        }
    }
}
