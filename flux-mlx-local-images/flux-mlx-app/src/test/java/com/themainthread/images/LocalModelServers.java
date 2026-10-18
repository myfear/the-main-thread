package com.themainthread.images;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class LocalModelServers implements QuarkusTestResourceLifecycleManager {
    static final ObjectMapper JSON = new ObjectMapper();
    static final AtomicReference<JsonNode> LAST_CHAT = new AtomicReference<>();
    static Path output;
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;

    @Override
    public Map<String, String> start() {
        try {
            output = Files.createTempDirectory("flux-images-test-");
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.createContext("/api/chat", exchange -> {
                JsonNode request = JSON.readTree(exchange.getRequestBody());
                LAST_CHAT.set(request);
                String finish = request.toString().contains("truncate") ? "length" : "stop";
                send(exchange, Map.of("model", "qwen2.5:7b", "done", true, "done_reason", finish,
                        "message", Map.of("role", "assistant", "content", "A blue ceramic camera on a warm wooden desk."),
                        "prompt_eval_count", 30, "eval_count", 14));
            });
            server.start();
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String classes = Path.of(RendererProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            return Map.of("quarkus.langchain4j.ollama.base-url", "http://127.0.0.1:" + server.getAddress().getPort(),
                    "images.command", String.join(",", java, "-cp", classes, RendererProcess.class.getName()),
                    "images.timeout", "3s", "images.output-directory", output.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void send(HttpExchange exchange, Object body) throws IOException {
        byte[] json = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, json.length);
        try (var stream = exchange.getResponseBody()) {
            stream.write(json);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
