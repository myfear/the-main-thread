package com.themainthread.codemode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

/** Owns the packaged server's stdin/stdout, so each test launches and closes its own JVM. */
class GatewayContractIT {
    @TempDir
    Path directory;

    @Test
    void standaloneSdkClientUsesTwoCallsAndPreservesSessionAndEnvelopes() throws Exception {
        try (var session = open()) {
            var tools = session.client.listTools().tools();
            assertEquals(1, tools.size());
            assertEquals("code", tools.getFirst().name());
            assertTrue(tools.getFirst().description().contains("quarkus_callTool"));
            JsonNode result = session.code("const s=quarkus.quarkus_status(" + args() + ");"
                    + "const l=quarkus.quarkus_logs(" + args() + ");"
                    + "return {status:s.content[0].text,errors:l.content[0].text.split('\\n').filter(x=>x.includes('DEMO_ERROR'))};");
            assertTrue(result.path("ok").asBoolean(), result.toString());
            assertEquals(2, result.path("metrics").path("nestedCalls").asInt());
            assertEquals("running", result.path("value").path("status").asText());
            assertEquals(1, result.path("value").path("errors").size());
            assertFalse(result.path("value").toString().contains("DEMO_INFO"));
            var envelope = session.code("return quarkus.quarkus_searchDocs({query:'error',projectDir:" + pathJson() + "});");
            assertTrue(envelope.path("ok").asBoolean());
            assertTrue(envelope.path("value").path("isError").asBoolean());
            assertEquals(2, envelope.path("value").path("content").size());
            assertTrue(envelope.path("value").path("futureField").path("kept").asBoolean());
            assertEquals(3, envelope.path("value").path("structuredContent").path("calls").asInt());
            assertEquals(result.path("metrics").path("upstreamPid"), envelope.path("metrics").path("upstreamPid"));
            assertDeadWorker(result);
            assertDeadWorker(envelope);
        }
    }

    @Test
    void rawBridgeCannotBypassPolicyAndMalformedScriptsRecover() throws Exception {
        try (var session = open()) {
            kind(session.code("return callTool('quarkus_start'," + args() + ");"), "DENIED");
            kind(session.code("return DynamicScript_Builtins.callTool('quarkus_stop',JSON.stringify(" + args() + "));"), "DENIED");
            kind(session.code("return quarkus.quarkus_callTool({projectDir:" + pathJson()
                    + ",toolName:'devui-tests_runAll',toolArguments:'{}'});"), "DENIED");
            kind(session.code("return quarkus.quarkus_status({projectDir:'/tmp'});"), "DENIED");
            kind(session.code("return quarkus.quarkus_logs({projectDir:" + pathJson() + ",lines:'wrong'});"), "INVALID_ARGUMENTS");
            kind(session.code("return {broken"), "SYNTAX_ERROR");
            kind(session.code("throw new Error('DO_NOT_LOG_SECRET_123');"), "SCRIPT_ERROR");
            assertEquals(42, session.code("return 42;").path("value").asInt());
            String trace = Files.readString(directory.resolve("gateway.jsonl"));
            assertFalse(trace.contains("DO_NOT_LOG_SECRET_123"));
            assertFalse(trace.contains(directory.toString()));
        }
    }

    @Test
    void timeoutsCrashesAndOversizedResponsesReplaceTheSessionAndRecover() throws Exception {
        try (var session = open()) {
            long oldPid = session.code("return 1;").path("metrics").path("upstreamPid").asLong();
            for (String mode : new String[] { "stall", "crash", "large" }) {
                JsonNode failure = session.code("return quarkus.quarkus_searchDocs({query:'" + mode + "'});");
                kind(failure, mode.equals("stall") ? "UPSTREAM_TIMEOUT" : mode.equals("crash") ? "UPSTREAM_EXIT" : "UPSTREAM_LIMIT");
                assertDeadWorker(failure);
                assertFalse(ProcessHandle.of(oldPid).map(ProcessHandle::isAlive).orElse(false));
                var recovered = session.code("return quarkus.quarkus_status(" + args() + ");");
                assertTrue(recovered.path("ok").asBoolean(), recovered.toString());
                long newPid = recovered.path("metrics").path("upstreamPid").asLong();
                assertTrue(newPid != oldPid);
                oldPid = newPid;
            }
            var loop = session.code("while(true){};");
            kind(loop, "EXECUTION_TIMEOUT");
            assertDeadWorker(loop);
            assertEquals("ready", session.code("return 'ready';").path("value").asText());
        }
    }

    @Test
    void failureReapsUpstreamDescendants() throws Exception {
        try (var session = open()) {
            var result = session.code("return quarkus.quarkus_searchDocs({query:'child'});");
            assertTrue(result.path("ok").asBoolean(), result.toString());
            long childPid = result.path("value").path("structuredContent").path("childPid").asLong();
            assertTrue(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false));
            kind(session.code("return quarkus.quarkus_searchDocs({query:'stall'});"), "UPSTREAM_TIMEOUT");
            assertFalse(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false));
            assertEquals(7, session.code("return 7;").path("value").asInt());
        }
    }

    @Test
    void cancellationTargetsTheMatchingRequestAndReapsTheWorker() throws Exception {
        try (var session = open(); var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = threads.submit(() -> session.code("while(true){};"));
            Thread.sleep(700);
            Object requestId = session.transport.lastRequestId;
            session.transport.sendMessage(new McpSchema.JSONRPCNotification("2.0", "notifications/cancelled",
                    Map.of("requestId", "unrelated"))).block();
            session.transport.sendMessage(new McpSchema.JSONRPCNotification("2.0", "notifications/cancelled",
                    Map.of())).block();
            Thread.sleep(100);
            assertFalse(pending.isDone(), "Unrelated cancellation must not cancel the active script");
            session.transport.sendMessage(new McpSchema.JSONRPCNotification("2.0", "notifications/cancelled",
                    Map.of("requestId", requestId))).block();
            JsonNode result = pending.get(5, TimeUnit.SECONDS);
            kind(result, "CANCELLED");
            assertDeadWorker(result);
            assertTrue(session.code("return 1;").path("ok").asBoolean());
        }
    }

    @Test
    void boundsScriptCallsOutputAndConcurrency() throws Exception {
        try (var session = open()) {
            kind(session.code("x".repeat(32769)), "SCRIPT_LIMIT");
            kind(session.code("return 'x'.repeat(20000);"), "RESULT_LIMIT");
            kind(session.code("for(let i=0;i<11;i++)quarkus.quarkus_status(" + args() + ");return 1;"), "CALL_LIMIT");
            try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
                var loop = threads.submit(() -> session.code("while(true){};"));
                Thread.sleep(700);
                kind(session.code("return 1;"), "BUSY");
                kind(loop.get(8, TimeUnit.SECONDS), "EXECUTION_TIMEOUT");
            }
            assertTrue(session.code("return true;").path("ok").asBoolean());
        }
    }

    @Test
    void eofDuringExecutionStopsThePackagedGatewayAndItsChildren() throws Exception {
        try (var threads = Executors.newVirtualThreadPerTaskExecutor(); var session = open()) {
            var pending = threads.submit(() -> session.code("while(true){};"));
            Thread.sleep(700);
            var children = session.transport.process.descendants().toList();
            assertTrue(children.size() >= 2, "Expected upstream and worker processes");
            session.transport.process.getOutputStream().close();
            assertTrue(session.transport.process.waitFor(10, TimeUnit.SECONDS));
            for (ProcessHandle child : children) {
                assertFalse(child.isAlive(), "Child survived gateway EOF: " + child.pid());
            }
            pending.cancel(true);
        }
    }

    private Session open() throws Exception {
        var transport = startGateway();
        var client = McpClient.sync(transport).initializationTimeout(Duration.ofSeconds(20)).requestTimeout(Duration.ofSeconds(12)).build();
        try {
            client.initialize();
            return new Session(transport, client);
        } catch (RuntimeException e) {
            transport.stop();
            throw e;
        }
    }

    @Test
    void newerClientProposalNegotiatesASupportedProtocol() throws Exception {
        var transport = startGateway();
        try (var reader = Executors.newVirtualThreadPerTaskExecutor()) {
            JsonLines.write(transport.process.getOutputStream(), Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize",
                    "params", Map.of("protocolVersion", "2025-11-25", "capabilities", Map.of(),
                            "clientInfo", Map.of("name", "newer-client", "version", "1"))));
            JsonNode response = reader.submit(() -> JsonLines.read(transport.process.getInputStream(), 65536)).get(10, TimeUnit.SECONDS);
            assertEquals("2025-11-25", response.path("result").path("protocolVersion").asText());
            transport.process.getOutputStream().close();
            assertTrue(transport.process.waitFor(10, TimeUnit.SECONDS));
        } finally {
            transport.stop();
        }
    }

    private OwnedTransport startGateway() throws Exception {
        Path launcher = directory.resolve("fake-jbang");
        String launch = ChildJvm.command(FakeCatalogServer.class.getName()).stream()
                .map(GatewayContractIT::shellQuote).collect(java.util.stream.Collectors.joining(" "));
        Files.writeString(launcher, "#!/bin/sh\nexec " + launch + "\n");
        assertTrue(launcher.toFile().setExecutable(true));
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin/java").toString());
        command.addAll(java.util.List.of("-Xmx256m", "-Dgateway.project=" + directory.toRealPath(),
                "-Dgateway.session=" + directory.toRealPath(), "-Dgateway.jbang=" + launcher.toRealPath(),
                "-Dgateway.tool-timeout-ms=700", "-Dgateway.execution-timeout-ms=3000",
                "-Dcodemode.worker.classpath=" + System.getProperty("java.class.path"),
                "-jar", Path.of("target/quarkus-app/quarkus-run.jar").toAbsolutePath().toString()));
        return new OwnedTransport(command, Path.of("").toAbsolutePath(), Map.of(), 65_536);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String pathJson() throws Exception {
        return Json.MAPPER.writeValueAsString(directory.toRealPath().toString());
    }

    private String args() throws Exception {
        return "{projectDir:" + pathJson() + "}";
    }

    static void kind(JsonNode result, String kind) {
        assertFalse(result.path("ok").asBoolean(), result.toString());
        assertEquals(kind, result.path("error").path("kind").asText(), result.toString());
        assertTrue(result.path("metrics").path("cleanedUp").asBoolean(), result.toString());
    }

    static void assertDeadWorker(JsonNode result) {
        long pid = result.path("metrics").path("workerPid").asLong();
        assertTrue(pid > 0);
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }

    record Session(OwnedTransport transport, McpSyncClient client) implements AutoCloseable {
        JsonNode code(String script) {
            var response = client.callTool(new McpSchema.CallToolRequest("code", Map.of("script", script)));
            return Json.MAPPER.valueToTree(response.structuredContent());
        }

        @Override
        public void close() throws Exception {
            transport.process.getOutputStream().close();
            boolean exited = transport.process.waitFor(10, TimeUnit.SECONDS);
            transport.stop();
            client.closeGracefully();
            assertTrue(exited, "Gateway must clean up and exit on stdin EOF");
        }
    }
}
