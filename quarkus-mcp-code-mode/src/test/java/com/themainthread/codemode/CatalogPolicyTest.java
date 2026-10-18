package com.themainthread.codemode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogPolicyTest {
    @TempDir
    Path directory;

    @Test
    void generatesExactNamesSchemasAndAdditionalProperties() throws Exception {
        var catalog = Catalog.pinned();
        assertEquals(20, catalog.schemas.size());
        for (String name : catalog.schemas.keySet()) {
            assertTrue(catalog.javascript.contains('"' + name + '"'));
            assertTrue(catalog.declarations.contains('"' + name + '"'));
        }
        assertTrue(catalog.declarations.contains("projectDir: string"));
        assertTrue(catalog.declarations.contains("lines?: number"));
        assertTrue(catalog.declarations.contains("[key: string]: unknown"));
        assertFalse(catalog.javascript.contains("devui-exceptions_getLastException"));
        catalog.write(directory);
        assertEquals(catalog.raw, Json.parse(Files.readString(directory.resolve("upstream-catalog.json"))));
        catalog.verify(catalog.raw);
        var changed = catalog.raw.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) changed.path("tools").get(0).path("inputSchema")).put("minimum", 1);
        assertThrows(CodeModeFailure.class, () -> catalog.verify(changed));
    }

    @Test
    void enforcesOuterNestedSchemaAndCanonicalPaths() throws Exception {
        var policy = new Policy(directory, Catalog.pinned());
        String path = directory.toRealPath().toString();
        policy.check("quarkus_status", Json.MAPPER.valueToTree(Map.of("projectDir", path)));
        denied(policy, "quarkus_start", Map.of("projectDir", path));
        denied(policy, "quarkus_app_log", Map.of("projectDir", path));
        denied(policy, "quarkus_status", Map.of("projectDir", path + "/../" + directory.getFileName()));
        denied(policy, "quarkus_status", Map.of("projectDir", "/tmp"));
        Files.createSymbolicLink(directory.resolve("escape"), Path.of("/tmp"));
        denied(policy, "quarkus_status", Map.of("projectDir", directory.resolve("escape").toString()));
        denied(policy, "quarkus_callTool", Map.of("projectDir", path, "toolName", "devui-tests_runAll", "toolArguments", "{}"));
        denied(policy, "quarkus_callTool", Map.of("projectDir", path, "toolName", "devui-exceptions_getLastException",
                "toolArguments", "{\"maxCauseDepth\":\"5\"}"));
        JsonNode diagnostic;
        try (var source = Policy.class.getResourceAsStream("/dev-mcp-diagnostic.json")) {
            diagnostic = Json.MAPPER.readTree(source);
        }
        policy.recordDiscovery(Json.MAPPER.valueToTree(Map.of("content", java.util.List.of(Map.of("type", "text",
                "text", "[" + diagnostic + "]")))));
        policy.check("quarkus_callTool", Json.MAPPER.valueToTree(Map.of("projectDir", path,
                "toolName", "devui-exceptions_getLastException", "toolArguments", "{\"maxCauseDepth\":\"5\"}")));
        denied(policy, "quarkus_callTool", Map.of("projectDir", path, "toolName", "devui-exceptions_getLastException", "toolArguments", "{}"));
        denied(policy, "quarkus_callTool", Map.of("projectDir", path, "toolName", "devui-exceptions_getLastException", "toolArguments", "{\"maxCauseDepth\":5}"));
        denied(policy, "quarkus_callTool", Map.of("projectDir", path, "toolName", "devui-exceptions_getLastException", "toolArguments", "{\"maxCauseDepth\":\"100\"}"));
        policy.resetDiscovery();
        denied(policy, "quarkus_callTool", Map.of("projectDir", path, "toolName", "devui-exceptions_getLastException", "toolArguments", "{\"maxCauseDepth\":\"5\"}"));
        denied(policy, "quarkus_callTool", Map.of("projectDir", path, "toolName", "devui-exceptions_getLastException",
                "toolArguments", "{\"projectDir\":\"/tmp\"}"));
        denied(policy, "quarkus_callTool", Map.of("projectDir", path, "toolName", "devui-exceptions_getLastException",
                "toolArguments", "{bad"));
        denied(policy, "quarkus_status", Map.of());
        denied(policy, "quarkus_logs", Map.of("projectDir", path, "lines", "10"));
        denied(policy, "quarkus_logs", Map.of("projectDir", path, "lines", 201));
    }

    @Test
    void boundsAndRedactsTraceRetention() throws Exception {
        var trace = new Trace(directory);
        for (int i = 0; i < 1100; i++) {
            trace.write(Map.of("event", "test", "hash", "a".repeat(1000)));
        }
        assertTrue(Files.exists(directory.resolve("gateway.previous.jsonl")));
        assertTrue(Files.size(directory.resolve("gateway.jsonl")) <= 1_048_576);
        assertTrue(Files.size(directory.resolve("gateway.previous.jsonl")) <= 1_048_576);
    }

    private static void denied(Policy policy, String name, Map<String, Object> args) {
        assertThrows(CodeModeFailure.class, () -> policy.check(name, Json.MAPPER.valueToTree(args)));
    }
}
