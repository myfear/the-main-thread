package com.themainthread.exoplanets;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class ArchiveStub implements QuarkusTestResourceLifecycleManager {
    private HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/TAP/sync", exchange -> {
                String query = java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
                if (!query.contains("default_flag=1") || !query.contains("format=json")) {
                    exchange.sendResponseHeaders(400, -1);
                } else {
                    byte[] body = """
                            [{"pl_name":"Fixture b","discoverymethod":"Transit","disc_year":2001,
                              "pl_orbper":3.5,"pl_orbperlim":0,"pl_rade":1.2,"pl_radelim":0}]
                            """.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            });
            server.start();
            return Map.of("quarkus.rest-client.nasa.url", "http://127.0.0.1:" + server.getAddress().getPort());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
