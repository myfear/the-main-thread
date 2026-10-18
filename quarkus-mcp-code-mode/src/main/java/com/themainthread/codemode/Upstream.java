package com.themainthread.codemode;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

final class Upstream implements AutoCloseable {
    final OwnedTransport transport;
    final McpSyncClient client;
    final String protocol;

    Upstream(List<String> command, Path directory, Map<String, String> environment, Duration timeout,
            Catalog catalog, Trace trace) throws IOException {
        transport = new OwnedTransport(command, directory, environment, 65_536);
        client = McpClient.sync(transport).requestTimeout(timeout).initializationTimeout(Duration.ofSeconds(20))
                .clientInfo(new McpSchema.Implementation("quarkus-code-mode", "0.2.0")).build();
        try {
            var init = client.initialize();
            protocol = init.protocolVersion();
            if (!OwnedTransport.PROTOCOLS.contains(protocol) || !init.serverInfo().name().equals("quarkus-agent")
                    || !init.serverInfo().version().equals("1.0.10")) {
                throw new CodeModeFailure("UPSTREAM_PROTOCOL", "Unexpected upstream identity or negotiated protocol.");
            }
            transport.lastResult = null;
            client.listTools();
            catalog.verify(transport.lastResult);
            trace.write(Map.of("event", "session", "pid", transport.process.pid(), "protocol", protocol,
                    "catalogHash", catalog.hash, "toolCount", catalog.schemas.size()));
        } catch (RuntimeException e) {
            close();
            if (e instanceof CodeModeFailure failure) {
                throw failure;
            }
            throw new CodeModeFailure("UPSTREAM_PROTOCOL", "Upstream initialization or catalog discovery failed.");
        }
    }

    JsonNode call(String name, JsonNode args) {
        if (transport.catalogChanged) {
            throw new CodeModeFailure("CATALOG_CHANGED", "Upstream announced changed tools; restart and audit the catalog.");
        }
        transport.lastResult = null;
        try {
            client.callTool(new McpSchema.CallToolRequest(name,
                    Json.MAPPER.convertValue(args, new TypeReference<Map<String, Object>>() { })));
            JsonNode raw = transport.lastResult;
            if (raw == null || !raw.isObject() || !raw.path("content").isArray()) {
                throw new CodeModeFailure("UPSTREAM_PROTOCOL", "Upstream returned an invalid tool envelope.");
            }
            return raw.deepCopy(); // Keep the wire envelope, including fields unknown to the SDK.
        } catch (RuntimeException e) {
            if (e instanceof CodeModeFailure failure) {
                throw failure;
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new CodeModeFailure("CANCELLED", "Execution was cancelled.");
            }
            throw new CodeModeFailure(transport.fault == null ? "UPSTREAM_TIMEOUT" : transport.fault,
                    "Upstream call failed or exceeded its deadline; the session will be replaced.");
        }
    }

    @Override
    public void close() {
        // Stop the owned tree before SDK shutdown so no SDK close can wait indefinitely on a child.
        transport.stop();
        client.closeGracefully();
    }
}
