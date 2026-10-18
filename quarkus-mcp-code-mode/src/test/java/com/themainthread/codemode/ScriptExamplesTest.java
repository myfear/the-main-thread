package com.themainthread.codemode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import io.quarkiverse.quickjs4j.ScriptInterfaceFactory;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class ScriptExamplesTest {
    @Inject
    ScriptInterfaceFactory<DynamicScript, HostBridge> factory;

    @Test
    void incidentExampleDeduplicatesAndSelectsHighestAttemptRegardlessOfOrder() throws Exception {
        List<String> events = Files.readAllLines(Path.of("demo-app/src/main/resources/events.jsonl"));
        JsonNode expected = Json.parse(Files.readString(Path.of("examples/incident-report.expected.json")));
        assertEquals(expected, report(events));
        var shuffled = new ArrayList<>(events);
        Collections.reverse(shuffled);
        shuffled.addAll(events);
        assertEquals(expected, report(shuffled));
    }

    @Test
    void incidentExampleHandlesAnEndpointWithoutSuccessAndRejectsMissingEvents() throws Exception {
        var report = report(List.of("""
                {"eventId":"x","requestId":"r","endpoint":"/pay","attempt":1,"outcome":"error","durationMs":90}
                """.strip()));
        assertEquals(Json.parse("""
                {"endpoints":[{"endpoint":"/pay","requestCount":1,"finalFailureCount":1,"p95DurationMs":null}],
                 "failedAfterRetry":[]}
                """), report);
        assertThrows(CodeModeFailure.class, () -> report(List.of()));
    }

    @Test
    void percentileUsesNearestRankRatherThanTheMaximum() throws Exception {
        var events = new ArrayList<String>();
        for (int i = 1; i <= 20; i++) {
            events.add(Json.MAPPER.valueToTree(Map.of("eventId", "e" + i, "requestId", "r" + i,
                    "endpoint", "/cart", "attempt", 1, "outcome", "ok", "durationMs", i)).toString());
        }
        assertEquals(19, report(events).path("endpoints").get(0).path("p95DurationMs").asInt());
    }

    @Test
    void triageExampleUsesTheInjectedProjectAndReturnsOnlyMatchingLogLines() throws Exception {
        var names = new ArrayList<String>();
        var bridge = new HostBridge((name, json) -> {
            names.add(name);
            assertEquals("/demo", Json.parse(json).path("projectDir").asText());
            return Json.MAPPER.valueToTree(Map.of("content", List.of(Map.of("type", "text", "text",
                    name.equals("quarkus_status") ? "running" : "noise\nDEMO_ERROR payment failed\nnoise")))).toString();
        });
        var result = run("triage.js", bridge);
        assertEquals(List.of("quarkus_status", "quarkus_logs"), names);
        assertEquals(Json.parse("[\"DEMO_ERROR payment failed\"]"), result.path("errors"));
    }

    private JsonNode report(List<String> events) throws Exception {
        String logs = "unrelated background message\n" + String.join("\n", events.stream().map(e -> "R " + e).toList());
        return run("incident-report.js", new HostBridge((name, json) -> {
            assertEquals("quarkus_logs", name);
            return Json.MAPPER.valueToTree(Map.of("content", List.of(Map.of("type", "text", "text", logs)))).toString();
        }));
    }

    @Test
    void investigationBranchesOnStatusAndPaymentErrorAndKeepsOnlyTheFinding() throws Exception {
        for (String status : List.of("not_started", "running (port: 18191)")) {
            for (boolean paymentError : List.of(false, true)) {
                var calls = new ArrayList<String>();
                var result = run("investigate.js", new HostBridge((name, json) -> {
                    calls.add(name);
                    JsonNode arguments = Json.parse(json);
                    assertEquals("/demo", arguments.path("projectDir").asText());
                    String content = switch (name) {
                        case "quarkus_status" -> status;
                        case "quarkus_logs" -> "noise\n" + (paymentError ? "DEMO_ERROR payment retry exhausted" : "DEMO_INFO ok");
                        case "quarkus_searchTools" -> "discovered exception diagnostic";
                        case "quarkus_callTool" -> {
                            assertEquals("devui-exceptions_getLastException", arguments.path("toolName").asText());
                            assertEquals("3", Json.parse(arguments.path("toolArguments").asText()).path("maxCauseDepth").asText());
                            yield "{\"hasException\":false,\"message\":\"No exceptions recorded\"}";
                        }
                        default -> throw new AssertionError(name);
                    };
                    return Json.MAPPER.valueToTree(Map.of("content", List.of(Map.of("type", "text", "text", content)))).toString();
                }));
                var expectedCalls = new ArrayList<>(List.of("quarkus_status"));
                if (status.startsWith("running")) {
                    expectedCalls.add("quarkus_logs");
                    if (paymentError) expectedCalls.addAll(List.of("quarkus_searchTools", "quarkus_callTool"));
                }
                assertEquals(expectedCalls, calls);
                assertEquals(status, result.path("status").asText());
                assertEquals(expectedCalls.size() == 4 ? 1 : 0, result.path("errors").size());
                assertEquals(expectedCalls.size() == 4 ? Json.parse("false") : Json.parse("null"), result.path("hasException"));
                assertEquals(3, result.size());
            }
        }
    }

    private JsonNode run(String file, HostBridge bridge) throws Exception {
        return DynamicRuntime.run(factory, Catalog.pinned().javascript + Files.readString(Path.of("examples", file)),
                "{\"projectDir\":\"/demo\"}", bridge, 16384);
    }
}
