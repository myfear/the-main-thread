package com.themainthread.vex;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Diagnostic only. STDIO protocol is kept separate from server stderr. */
final class HuginnProbe {
    ObjectNode check(Path project, Path serverJar) throws Exception {
        List<String> command = serverJar == null
                ? List.of("jbang", "--quiet", "eu.maveniverse.maven.huginn:huginn-mcp:0.1.1:runner")
                : List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-jar", serverJar.toRealPath().toString());
        var builder = new ProcessBuilder(command).directory(project.toRealPath().toFile()).redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("HUGINN_PROJECT", project.toRealPath().toString());
        Process process = builder.start();
        try (var writer = new PrintWriter(process.getOutputStream(), true, StandardCharsets.UTF_8);
                var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> {
                var result = EvidenceHasher.object();
                result.set("initialize", call(writer, reader, 1, "initialize", EvidenceHasher.JSON.readTree("{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"vex-preflight\",\"version\":\"1.0.0\"}}")));
                writer.println("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
                JsonNode tools = call(writer, reader, 2, "tools/list", EvidenceHasher.object());
                result.set("tools", tools);
                var names = new java.util.HashSet<String>();
                for (var tool : tools.path("result").path("tools")) names.add(tool.path("name").asText());
                EvidenceHasher.require(names.containsAll(List.of("describe", "dependencies", "why", "sources", "versions")), "Huginn tool contract changed");
                int id = 3;
                for (String tool : List.of("describe", "dependencies", "why")) {
                    var params = EvidenceHasher.object();
                    params.put("name", tool);
                    var arguments = params.putObject("arguments");
                    arguments.put("project", project.toRealPath().toString()).put("module", "sample-app");
                    if (tool.equals("why")) arguments.put("artifact", "org.apache.commons:commons-text");
                    result.set(tool, call(writer, reader, id++, "tools/call", params));
                }
                return result;
            });
            try { return future.get(Duration.ofSeconds(90).toMillis(), TimeUnit.MILLISECONDS); }
            catch (Exception e) { process.destroyForcibly(); future.cancel(true); throw e; }
        } finally { process.destroyForcibly(); }
    }

    private JsonNode call(PrintWriter writer, BufferedReader reader, int id, String method, JsonNode parameters) throws Exception {
        var request = EvidenceHasher.object();
        request.put("jsonrpc", "2.0").put("id", id).put("method", method).set("params", parameters);
        writer.println(request);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() > EvidenceHasher.MAX_JSON_BYTES) throw new CaseException(4, "Oversized MCP response");
            JsonNode response = EvidenceHasher.JSON.readTree(line);
            if (response.path("id").asInt(-1) != id) continue;
            EvidenceHasher.require(!response.has("error") && !response.path("result").path("isError").asBoolean(), "Huginn rejected " + method + ": " + response);
            return response;
        }
        throw new CaseException(4, "Huginn closed STDIO before replying");
    }
}
