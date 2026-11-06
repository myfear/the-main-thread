// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class FakeDbpedia implements QuarkusTestResourceLifecycleManager {
    record Reply(int status, String type, byte[] body, boolean chunked, Map<String, String> headers,
            int headerDelay, int bodyDelay) {
    }

    static final AtomicInteger calls = new AtomicInteger();
    static final AtomicInteger externalEntityCalls = new AtomicInteger();
    static volatile Reply reply;
    static volatile String method;
    static volatile String path;
    static volatile String accept;
    static volatile String acceptEncoding;
    static volatile String baseUrl;
    private HttpServer server;
    private ExecutorService executor;

    static void reset() {
        calls.set(0);
        externalEntityCalls.set(0);
        fixture("wrong-airport-first");
    }

    static void fixture(String name) {
        response(200, "application/rdf+xml; charset=UTF-8", Fixtures.bytes(name), true);
    }

    static void response(int status, String type, byte[] body, boolean chunked) {
        reply = new Reply(status, type, body, chunked, Map.of(), 0, 0);
    }

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.createContext("/data/", this::respond);
            server.createContext("/external-entity", exchange -> {
                externalEntityCalls.incrementAndGet();
                try (exchange) {
                    byte[] secret = "must-not-be-returned".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, secret.length);
                    exchange.getResponseBody().write(secret);
                }
            });
            server.start();
            baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            reset();
            return Map.of("quarkus.rest-client.dbpedia.url", baseUrl,
                    "quarkus.rest-client.dbpedia.read-timeout", "500",
                    "mainthread.dbpedia.body-timeout", "500ms",
                    "mainthread.dbpedia.max-bytes", "2048");
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            calls.incrementAndGet();
            method = exchange.getRequestMethod();
            path = exchange.getRequestURI().getPath();
            accept = exchange.getRequestHeaders().getFirst("Accept");
            acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
            Reply selected = reply;
            delay(selected.headerDelay());
            exchange.getResponseHeaders().set("Content-Type", selected.type());
            selected.headers().forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
            exchange.sendResponseHeaders(selected.status(), selected.chunked() ? 0 : selected.body().length);
            if (selected.bodyDelay() > 0) {
                exchange.getResponseBody().write('x');
                exchange.getResponseBody().flush();
                delay(selected.bodyDelay());
            }
            exchange.getResponseBody().write(selected.body());
        }
    }

    private static void delay(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
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
