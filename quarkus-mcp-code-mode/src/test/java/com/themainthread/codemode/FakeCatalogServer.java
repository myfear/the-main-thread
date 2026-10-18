package com.themainthread.codemode;

import java.util.List;
import java.util.Map;

/** Wire fixture intentionally preserves extra result fields that typed SDK records cannot represent. */
public class FakeCatalogServer {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("sleep")) {
            Thread.sleep(60_000);
            return;
        }
        int calls = 0;
        while (true) {
            var request = JsonLines.read(System.in, 65_536);
            if (!request.has("id")) {
                continue;
            }
            Object result;
            switch (request.path("method").asText()) {
                case "initialize" -> result = Map.of("protocolVersion", "2025-03-26", "capabilities", Map.of("tools", Map.of()),
                        "serverInfo", Map.of("name", "quarkus-agent", "version", "1.0.10"));
                case "tools/list" -> result = Catalog.pinned().raw;
                case "tools/call" -> {
                    calls++;
                    var arguments = request.path("params").path("arguments");
                    String mode = arguments.path("query").asText();
                    long childPid = 0;
                    if (mode.equals("child")) {
                        childPid = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin/java").toString(),
                                "-cp", System.getProperty("java.class.path"), FakeCatalogServer.class.getName(), "sleep")
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start().pid();
                    }
                    if (mode.equals("stall")) {
                        Thread.sleep(60_000);
                    }
                    if (mode.equals("crash")) {
                        Runtime.getRuntime().halt(3);
                    }
                    String text = switch (mode) {
                        case "large" -> "x".repeat(70_000);
                        case "error" -> "upstream diagnostic";
                        default -> request.path("params").path("name").asText().equals("quarkus_status")
                                ? "running" : "DEMO_INFO irrelevant\nDEMO_ERROR missing inventory price at ItemsResource.items\nDEMO_INFO more";
                    };
                    if (request.path("params").path("name").asText().equals("quarkus_searchTools")) {
                        try (var source = Policy.class.getResourceAsStream("/dev-mcp-diagnostic.json")) {
                            text = "[" + Json.MAPPER.readTree(source).toString() + "]";
                        }
                    }
                    result = Map.of("content", List.of(Map.of("type", "text", "text", text),
                            Map.of("type", "resource", "resource", Map.of("uri", "test://fixture", "text", "supplement", "mimeType", "text/plain"))),
                            "isError", mode.equals("error"), "structuredContent", Map.of("calls", calls, "pid", ProcessHandle.current().pid(),
                                    "echo", arguments, "childPid", childPid), "futureField", Map.of("kept", true));
                }
                default -> result = Map.of();
            }
            JsonLines.write(System.out, Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result));
        }
    }
}
