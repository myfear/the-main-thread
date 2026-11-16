package com.themainthread.atem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class MediaMtxStub implements QuarkusTestResourceLifecycleManager {
    static final String STATUS = "/v3/paths/get/live/atem";
    static final String LIST = "/list";
    static final String GET = "/get";

    private static final Map<String, Reply> REPLIES = new ConcurrentHashMap<>();
    private static final Map<String, String> QUERIES = new ConcurrentHashMap<>();
    private HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                String query = exchange.getRequestURI().getRawQuery();
                QUERIES.put(path, query == null ? "" : query);
                Reply reply = REPLIES.getOrDefault(path, new Reply(404, "application/json", "{}"));
                byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", reply.contentType());
                exchange.sendResponseHeaders(reply.status(), body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        return Map.of("atem.control-url", origin, "atem.playback-url", origin);
    }

    @Override
    public void stop() {
        server.stop(0);
    }

    static void reset() {
        REPLIES.clear();
        QUERIES.clear();
    }

    static void json(String path, int status, String body) {
        REPLIES.put(path, new Reply(status, "application/json", body));
    }

    static void mp4(String body) {
        REPLIES.put(GET, new Reply(200, "video/mp4", body));
    }

    static String query(String path) {
        return QUERIES.get(path);
    }

    private record Reply(int status, String contentType, String body) {
    }
}
