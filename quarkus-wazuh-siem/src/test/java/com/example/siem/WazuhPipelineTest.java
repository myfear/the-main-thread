package com.example.siem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Checks real containers and a separately running dev application; never starts a fake SIEM. */
@EnabledIfSystemProperty(named = "wazuh.verify", matches = "true")
class WazuhPipelineTest {
    private static final Path RECEIPT_DIRECTORY = Path.of("target/wazuh-verification");
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String manager;
    private String indexer;

    @Test
    void rulesCollectionAndIndexedAlerts() throws Exception {
        Files.createDirectories(RECEIPT_DIRECTORY);
        manager = container("wazuh.manager");
        indexer = container("wazuh.indexer");
        String dashboard = container("wazuh.dashboard");
        await("healthy manager, indexer and dashboard", () -> healthy(manager) && healthy(indexer) && healthy(dashboard));
        String config = exec(manager, "", "cat", "/var/ossec/etc/ossec.conf");
        assertTrue(config.contains("/var/log/quarkus/security-events.jsonl"), "Missing active log collection config");
        assertTrue(config.contains("<only-future-events>no</only-future-events>"));
        assertEquals(Files.readString(Path.of("infra/wazuh/rules/quarkus_rules.xml")),
                exec(manager, "", "cat", "/var/ossec/etc/rules/quarkus_rules.xml"), "Rules were not imported");
        exec(manager, "", "test", "-r", "/var/log/quarkus/security-events.jsonl");
        verifyRules();
        if (Boolean.getBoolean("wazuh.rotation")) {
            verifyRotation();
            return;
        }

        request("GET", "/api/profile", "alice:alice-dev-only", 200);
        String failed = request("GET", "/api/profile", "alice:wrong-password", 401);
        String denied = request("POST", "/api/admin/export", "alice:alice-dev-only", 403);
        String exported = request("POST", "/api/admin/export", "operator:operator-dev-only", 200);
        List<String> failures = new ArrayList<>();
        Instant start = Instant.now();
        for (int i = 0; i < 5; i++) {
            failures.add(request("GET", "/api/profile", "alice:wrong-password", 401));
        }
        assertTrue(Duration.between(start, Instant.now()).toSeconds() < 60, "The request batch exceeded 60 seconds");
        for (String id : List.of(failed, denied, exported)) {
            assertTrue(auditLines().stream()
                    .map(this::json).anyMatch(e -> e.path("request_id").asText().equals(id)),
                    "Audit file is missing request " + id + "; restart dev mode if continuous testing detached its handler");
        }
        for (String line : auditLines()) {
            assertEquals(1, json(line).path("schema_version").asInt());
        }
        await("manager denial and export alerts", () -> {
            String alerts = exec(manager, "", "tail", "-n", "5000", "/var/ossec/logs/alerts/alerts.json");
            return hasAlert(alerts, denied, "100102") && hasAlert(alerts, exported, "100104");
        });
        List<String> queryIds = new ArrayList<>(List.of(denied, exported));
        queryIds.addAll(failures);
        await("indexed denial, completed export, and same-source correlation", () -> {
            JsonNode hits = indexed(queryIds.toArray(String[]::new)).path("hits").path("hits");
            boolean denial = false;
            boolean export = false;
            boolean correlation = false;
            for (JsonNode hit : hits) {
                JsonNode alert = hit.path("_source");
                String id = alert.path("data").path("request_id").asText();
                String rule = alert.path("rule").path("id").asText();
                denial |= id.equals(denied) && rule.equals("100102");
                export |= id.equals(exported) && rule.equals("100104");
                correlation |= failures.contains(id) && rule.equals("100103");
            }
            return denial && export && correlation;
        });
        JsonNode indexedAlerts = indexed(queryIds.toArray(String[]::new));
        var receipt = Map.of("validated_at", Instant.now().toString(), "denied_request_id", denied,
                "export_request_id", exported, "authentication_failure_request_ids", failures,
                "indexed_alerts", indexedAlerts);
        mapper.writerWithDefaultPrettyPrinter().writeValue(RECEIPT_DIRECTORY.resolve("receipt.json").toFile(), receipt);
        verifyManagerOutage(denied);
    }

    private boolean healthy(String id) throws Exception {
        JsonNode state = mapper.readTree(command("", "podman", "inspect", id)).get(0).path("State");
        return state.path("Running").asBoolean() && state.path("Health").path("Status").asText().equals("healthy");
    }

    private void verifyManagerOutage(String earlierDenial) throws Exception {
        String offlineExport;
        command("", "podman", "stop", "--time", "30", manager);
        try {
            offlineExport = request("POST", "/api/admin/export", "operator:operator-dev-only", 200);
            assertTrue(auditLines().stream()
                    .map(this::json).anyMatch(e -> e.path("request_id").asText().equals(offlineExport)),
                    "The request must still append its local audit record while the manager is stopped");
        } finally {
            command("", "podman", "start", manager);
        }
        await("healthy manager after restart", () -> healthy(manager));
        assertTrue(exec(manager, "", "cat", "/var/ossec/etc/ossec.conf")
                .contains("/var/log/quarkus/security-events.jsonl"));
        await("indexing the export completed during the manager outage", () ->
                indexed(offlineExport).path("hits").path("total").path("value").asInt() > 0);
        mapper.writerWithDefaultPrettyPrinter().writeValue(RECEIPT_DIRECTORY.resolve("outage-receipt.json").toFile(),
                Map.of("validated_at", Instant.now().toString(), "offline_export_request_id", offlineExport,
                        "indexed_export", indexed(offlineExport), "earlier_denial_after_restart", indexed(earlierDenial)));
    }

    private void verifyRotation() throws Exception {
        // Run with the dev handler's size reduced to 2K; never append fabricated events to the file.
        Path backup = Path.of("target/siem/security-events.jsonl.1");
        String before = Files.exists(backup) ? Files.readString(backup) : "";
        boolean rotated = false;
        for (int i = 0; i < 30; i++) {
            request("GET", "/api/profile", "alice:alice-dev-only", 200);
            if (Files.exists(backup) && !Files.readString(backup).equals(before)) {
                rotated = true;
                break;
            }
        }
        assertTrue(rotated, "No rollover detected; set QUARKUS_LOG_HANDLER_FILE_AUDIT_ROTATION_MAX_FILE_SIZE=2K for dev mode");
        List<String> exports = new ArrayList<>();
        await("an indexed export after file rotation", () -> {
            String id = request("POST", "/api/admin/export", "operator:operator-dev-only", 200);
            exports.add(id);
            return indexed(exports.toArray(String[]::new)).path("hits").path("total").path("value").asInt() > 0;
        });
        mapper.writerWithDefaultPrettyPrinter().writeValue(RECEIPT_DIRECTORY.resolve("rotation-receipt.json").toFile(),
                Map.of("validated_at", Instant.now().toString(), "post_rotation_export_ids", exports,
                        "indexed_exports", indexed(exports.toArray(String[]::new))));
    }

    private List<String> auditLines() throws Exception {
        List<String> lines = new ArrayList<>();
        try (var paths = Files.list(Path.of("target/siem"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().startsWith("security-events.jsonl")).toList()) {
                lines.addAll(Files.readAllLines(path));
            }
        }
        return lines;
    }

    private void verifyRules() throws Exception {
        assertRule(sample("AUTHENTICATION_FAILURE", "FAILURE", "192.0.2.10", "/api/profile", "quarkus-siem-demo"), "100101", "5");
        assertRule(sample("AUTHORIZATION_FAILURE", "DENIED", "192.0.2.10", "/api/admin/export", "quarkus-siem-demo"), "100102", "8");
        assertRule(sample("SENSITIVE_OPERATION_COMPLETED", "SUCCESS", "192.0.2.10", "/api/admin/export", "quarkus-siem-demo"), "100104", "3");
        String other = logtest(sample("AUTHENTICATION_FAILURE", "FAILURE", "192.0.2.10", "/api/profile", "another-service"));
        assertFalse(other.contains("id: '100101'"), "Another service matched the Quarkus rule");
        String wrongPath = logtest(sample("AUTHORIZATION_FAILURE", "DENIED", "192.0.2.10", "/api/profile", "quarkus-siem-demo"));
        assertFalse(wrongPath.contains("id: '100102'"));
        StringBuilder same = new StringBuilder();
        StringBuilder mixed = new StringBuilder();
        StringBuilder missingSource = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            same.append(sample("AUTHENTICATION_FAILURE", "FAILURE", "192.0.2.20", "/api/profile", "quarkus-siem-demo"));
            mixed.append(sample("AUTHENTICATION_FAILURE", "FAILURE", "192.0.2." + (30 + i), "/api/profile", "quarkus-siem-demo"));
            missingSource.append(sample("AUTHENTICATION_FAILURE", "FAILURE", null, "/api/profile", "quarkus-siem-demo"));
        }
        List<String> ids = ruleIds(logtest(same.toString()));
        assertEquals(List.of("100101", "100101", "100101", "100101", "100103"), ids,
                "The threshold must fire on the fifth same-source event");
        assertFalse(ruleIds(logtest(mixed.toString())).contains("100103"), "Mixed sources correlated");
        assertFalse(ruleIds(logtest(missingSource.toString())).contains("100103"), "Missing sources correlated");
    }

    private void assertRule(String line, String id, String level) throws Exception {
        String output = logtest(line);
        assertTrue(output.contains("id: '" + id + "'"), output);
        assertTrue(output.contains("level: '" + level + "'"), output);
    }

    private List<String> ruleIds(String output) {
        var matcher = Pattern.compile("(?m)^\\s*id: '(10010[0-4])'").matcher(output);
        List<String> ids = new ArrayList<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private String sample(String type, String outcome, String source, String path, String service) throws Exception {
        var record = mapper.createObjectNode().put("schema_version", 1).put("event_id", UUID.randomUUID().toString())
                .put("timestamp", Instant.now().toString()).put("service", service).put("audit_event_type", type)
                .put("outcome", outcome).put("principal", "alice").put("source_ip", source)
                .put("method", path.equals("/api/profile") ? "GET" : "POST").put("path", path)
                .put("request_id", UUID.randomUUID().toString());
        return mapper.writeValueAsString(record) + "\n";
    }

    private String logtest(String lines) throws Exception {
        return exec(manager, lines, "/var/ossec/bin/wazuh-logtest", "-v");
    }

    private String request(String method, String path, String credentials, int expected) throws Exception {
        String base = System.getProperty("wazuh.app-url", "http://127.0.0.1:8088");
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, response.statusCode(), method + " " + path);
        return response.headers().firstValue("X-Audit-Request-ID").orElseThrow();
    }

    private boolean hasAlert(String lines, String requestId, String rule) {
        return lines.lines().map(this::json).anyMatch(a -> a.path("data").path("request_id").asText().equals(requestId)
                && a.path("rule").path("id").asText().equals(rule));
    }

    private JsonNode indexed(String... ids) throws Exception {
        String query = mapper.writeValueAsString(Map.of("size", 100,
                "query", Map.of("terms", Map.of("data.request_id", List.of(ids)))));
        return json(exec(indexer, query, "curl", "-fsSk", "-u", "admin:SecretPassword", "-H", "Content-Type: application/json",
                "--data-binary", "@-", "https://localhost:9200/wazuh-alerts-*/_search"));
    }

    private String container(String service) throws Exception {
        String id = command("", "podman", "ps", "--filter", "label=com.docker.compose.project=quarkus-wazuh-siem",
                "--filter", "label=com.docker.compose.service=" + service, "--format", "{{.ID}}").trim();
        assertTrue(!id.isEmpty() && !id.contains("\n"), "Expected one running " + service + "; start Quarkus dev mode first");
        return id;
    }

    private String exec(String id, String input, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("podman", "exec", "-i", id));
        command.addAll(List.of(arguments));
        return command(input, command.toArray(String[]::new));
    }

    private String command(String input, String... arguments) throws Exception {
        Process process = new ProcessBuilder(arguments).start();
        CompletableFuture<String> stdout = new CompletableFuture<>();
        CompletableFuture<String> stderr = new CompletableFuture<>();
        Thread.startVirtualThread(() -> read(process.getInputStream(), stdout));
        Thread.startVirtualThread(() -> read(process.getErrorStream(), stderr));
        try (var stream = process.getOutputStream()) {
            stream.write(input.getBytes(StandardCharsets.UTF_8));
        }
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Timed out: " + arguments[0]);
        }
        String output = stdout.get(5, TimeUnit.SECONDS);
        String error = stderr.get(5, TimeUnit.SECONDS);
        assertEquals(0, process.exitValue(), "Container command failed: " + error);
        return List.of(arguments).contains("/var/ossec/bin/wazuh-logtest") ? output + error : output;
    }

    private static void read(java.io.InputStream stream, CompletableFuture<String> result) {
        try (stream) {
            result.complete(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception failure) {
            result.completeExceptionally(failure);
        }
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value);
        } catch (Exception failure) {
            throw new AssertionError("Invalid JSON in the audit or alert stream", failure);
        }
    }

    private void await(String description, CheckedCondition condition) throws Exception {
        Instant deadline = Instant.now().plusSeconds(120);
        Throwable lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                if (condition.get()) {
                    return;
                }
            } catch (Exception | AssertionError failure) {
                lastFailure = failure;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Timed out waiting for " + description + "; inspect manager/Filebeat/indexer logs", lastFailure);
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean get() throws Exception;
    }
}
