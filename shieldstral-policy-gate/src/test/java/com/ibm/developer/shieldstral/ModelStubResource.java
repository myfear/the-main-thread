package com.ibm.developer.shieldstral;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public final class ModelStubResource implements QuarkusTestResourceLifecycleManager {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger ASSISTANT_CALLS = new AtomicInteger();

    private HttpServer server;
    private ExecutorService executor;

    @Override
    public Map<String, String> start() {
        try {
            ASSISTANT_CALLS.set(0);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", this::handleChatCompletion);
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            server.start();

            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            return Map.of(
                    "shieldstral.base-url", baseUrl,
                    "shieldstral.api-key", "test-key",
                    "assistant.base-url", baseUrl,
                    "assistant.api-key", "test-key");
        } catch (IOException failure) {
            throw new IllegalStateException("Could not start the model stub", failure);
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

    static int assistantCalls() {
        return ASSISTANT_CALLS.get();
    }

    private void handleChatCompletion(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.contains("[classifier-down]")) {
            send(exchange, 503, Map.of("error", Map.of("message", "classifier unavailable")));
            return;
        }

        JsonNode request = JSON.readTree(body);
        String model = request.path("model").asText();
        if (model.contains("Shieldstral")) {
            sendShieldstralResponse(exchange, body);
        } else {
            sendAssistantResponse(exchange, body, model);
        }
    }

    private void sendShieldstralResponse(HttpExchange exchange, String requestBody) throws IOException {
        boolean publicSecurityPlaybook = requestBody.contains("public customer-support")
                && requestBody.contains("credential-stuffing simulation");
        boolean unsafeOutput = requestBody.contains("sk_live_example") || requestBody.contains("SSN 123-45-6789");
        double unsafeScore = publicSecurityPlaybook || unsafeOutput ? 0.95 : 0.05;
        String answer = unsafeScore > 0.5 ? "yes" : "no";

        Map<String, Object> firstToken = Map.of(
                "token", answer,
                "logprob", Math.log(unsafeScore > 0.5 ? unsafeScore : 1.0 - unsafeScore),
                "bytes", List.of(),
                "top_logprobs", List.of(
                        Map.of("token", "yes", "logprob", Math.log(unsafeScore), "bytes", List.of()),
                        Map.of("token", "no", "logprob", Math.log(1.0 - unsafeScore), "bytes", List.of())));

        Map<String, Object> choice = Map.of(
                "index", 0,
                "message", Map.of("role", "assistant", "content", answer),
                "logprobs", Map.of("content", List.of(firstToken)),
                "finish_reason", "length");

        send(exchange, 200, completion("mistralai/Shieldstral-1.0-3B", choice));
    }

    private void sendAssistantResponse(HttpExchange exchange, String requestBody, String model) throws IOException {
        ASSISTANT_CALLS.incrementAndGet();
        String answer = requestBody.contains("[unsafe-output]")
                ? "Use sk_live_example and the customer SSN 123-45-6789."
                : "Run the simulation only in an isolated lab, use synthetic accounts, and alert on repeated failures.";
        Map<String, Object> choice = Map.of(
                "index", 0,
                "message", Map.of("role", "assistant", "content", answer),
                "finish_reason", "stop");
        send(exchange, 200, completion(model, choice));
    }

    private static Map<String, Object> completion(String model, Map<String, Object> choice) {
        return Map.of(
                "id", "test-completion",
                "object", "chat.completion",
                "created", 1_786_614_400,
                "model", model,
                "choices", List.of(choice),
                "usage", Map.of("prompt_tokens", 12, "completion_tokens", 8, "total_tokens", 20));
    }

    private static void send(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] response = JSON.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }
}
