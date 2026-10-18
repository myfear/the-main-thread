package com.themainthread.codemode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpSchema;

/** Runs a saved script through the packaged server using the real MCP protocol. */
public final class ExampleClient {
    private ExampleClient() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Supply one or more JavaScript files");
        Path root = Path.of("").toRealPath();
        var transport = new OwnedTransport(List.of(root.resolve("scripts/start-mcp.sh").toString()),
                root, System.getenv(), 262_144);
        var client = McpClient.sync(transport).initializationTimeout(Duration.ofSeconds(180))
                .requestTimeout(Duration.ofSeconds(90)).build();
        try {
            client.initialize();
            var tools = client.listTools().tools();
            if (tools.size() != 1 || !tools.getFirst().name().equals("code")) {
                throw new IllegalStateException("Expected the code tool");
            }
            for (String file : args) {
                var result = client.callTool(new McpSchema.CallToolRequest("code",
                        Map.of("script", Files.readString(Path.of(file)))));
                var envelope = Json.MAPPER.valueToTree(result.structuredContent());
                // Standard output is the requested machine-readable result, not application logging.
                System.out.println(envelope.toPrettyString());
                if (!envelope.path("ok").asBoolean()) throw new IllegalStateException("Code execution failed");
                Path expected = Path.of(file.replaceFirst("\\.js$", ".expected.json"));
                if (Files.isRegularFile(expected)
                        && !envelope.path("value").equals(Json.parse(Files.readString(expected)))) {
                    throw new IllegalStateException("Result differs from " + expected);
                }
                long worker = envelope.path("metrics").path("workerPid").asLong();
                if (worker <= 0 || ProcessHandle.of(worker).map(ProcessHandle::isAlive).orElse(false)) {
                    throw new IllegalStateException("Worker was not reaped");
                }
            }
        } finally {
            transport.process.getOutputStream().close();
            boolean exited = transport.process.waitFor(15, TimeUnit.SECONDS);
            transport.stop();
            client.closeGracefully();
            if (!exited) throw new IllegalStateException("Server did not exit on stdin EOF");
        }
    }
}
