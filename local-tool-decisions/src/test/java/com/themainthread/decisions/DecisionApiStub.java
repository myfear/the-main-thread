package com.themainthread.decisions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/** Exercises the actual LangChain4j adapter through its HTTP boundary. */
public class DecisionApiStub implements QuarkusTestResourceLifecycleManager {
    record Request(String method, String path, String authorization, String body) {
    }

    private record Reply(int status, String body, long delayMillis) {
    }

    private static final List<Request> REQUESTS = new CopyOnWriteArrayList<>();
    private static volatile Reply reply;
    private HttpServer server;
    private ExecutorService executor;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            server.createContext("/", this::respond);
            reset();
            server.start();
            return Map.of(
                    "decisions.base-url", "http://127.0.0.1:" + server.getAddress().getPort(),
                    "decisions.model", "tev1:test",
                    "decisions.timeout", "250ms");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start the decision API stub", e);
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

    static void reset() {
        REQUESTS.clear();
        answer("REFUND", 0.01, 0.98, 0.01, 0.97);
    }

    static List<Request> requests() {
        return List.copyOf(REQUESTS);
    }

    static void answer(String intent, double lookup, double refund, double other, double alignment) {
        respondWith(200, response(intent, lookup, refund, other, alignment), 0);
    }

    static String response(String intent, double lookup, double refund, double other, double alignment) {
        return """
                {
                  "model": "tev1:test",
                  "answers": {
                    "intent": {
                      "type": "choice", "choice": "%s",
                      "probabilities": {"ORDER_LOOKUP": %s, "REFUND": %s, "OTHER": %s},
                      "confidence": 0.91
                    },
                    "aligned": {"type": "noul", "noul": %s}
                  },
                  "usage": {"input_tokens": 300, "output_tokens": 3}
                }
                """.formatted(intent, lookup, refund, other, alignment);
    }

    static void respondWith(int status, String body, long delayMillis) {
        reply = new Reply(status, body, delayMillis);
    }

    private void respond(HttpExchange exchange) throws IOException {
        Reply current = reply;
        REQUESTS.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        try (exchange) {
            if (current.delayMillis() > 0) {
                try {
                    Thread.sleep(current.delayMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            byte[] body = current.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(current.status(), body.length);
            exchange.getResponseBody().write(body);
        } catch (IOException e) {
            // A timed-out client may close its socket before the delayed reply is written.
            if (current.delayMillis() == 0) {
                throw e;
            }
        }
    }
}
