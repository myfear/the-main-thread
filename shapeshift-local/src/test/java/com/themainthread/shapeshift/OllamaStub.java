package com.themainthread.shapeshift;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class OllamaStub implements QuarkusTestResourceLifecycleManager {
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            var requests = new AtomicInteger();
            server.createContext("/requests", exchange -> {
                byte[] body = Integer.toString(requests.get()).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.createContext("/v1/systemone", exchange -> {
                requests.incrementAndGet();
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (request.contains("slow")) {
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                String chosen = request.contains("malformed") ? "UNEXPECTED" : "TIMER";
                byte[] body = ("""
                        {"model":"tev1:4b","answers":{"intent":{"type":"choice","choice":"%s",
                        "probabilities":{"EVENT":0.01,"REMINDER":0.01,"CHECKLIST":0.01,"TIMER":0.94,"SPLIT":0.01,"NOTE":0.01,"NONE":0.01},"confidence":0.88},
                        "urgent":{"type":"noul","noul":0.02}},"usage":{"input_tokens":120,"output_tokens":2}}
                        """)
                        .formatted(chosen).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return Map.of("shape.ollama-url", "http://127.0.0.1:" + server.getAddress().getPort());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }
}
