package com.themainthread.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RetryPolicyTest {
    @Inject
    QuoteClient client;

    enum Scenario {
        first_two_503, timed_outage, always_503
    }

    enum Policy {
        once, immediate, fixed, exponential, fibonacci
    }

    record Attempt(long offsetNanos, int status) {
    }

    static class Carrier {
        private final List<Attempt> attempts = new ArrayList<>();
        private final long recoveryNanos;
        private Scenario scenario;
        private long firstArrival;

        Carrier(int recoveryMs) {
            recoveryNanos = TimeUnit.MILLISECONDS.toNanos(recoveryMs);
        }

        synchronized void reset(Scenario next) {
            scenario = next;
            attempts.clear();
        }

        synchronized List<Attempt> snapshot() {
            return List.copyOf(attempts);
        }

        synchronized int recordArrival() {
            long now = System.nanoTime();
            if (attempts.isEmpty()) {
                firstArrival = now;
            }
            long offset = now - firstArrival;
            int status = switch (scenario) {
                case first_two_503 -> attempts.size() < 2 ? 503 : 200;
                case timed_outage -> offset < recoveryNanos ? 503 : 200;
                case always_503 -> 503;
            };
            attempts.add(new Attempt(offset, status));
            return status;
        }

        void respond(HttpExchange exchange) throws IOException {
            try (exchange) {
                exchange.sendResponseHeaders(recordArrival(), -1);
            }
        }
    }

    @Test
    void compareSchedules() throws Exception {
        int recoveryMs = Integer.parseInt(System.getProperty("retry.lab.recoveryMs", "250"));
        assertTrue(recoveryMs > 0, "retry.lab.recoveryMs must be positive");
        var carrier = new Carrier(recoveryMs);
        var rows = new ArrayList<String>();
        rows.add("round,scenario,policy,requests,outcome,elapsed_ms,arrival_ms,statuses");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/quote", carrier::respond);
        server.start();
        try {
            var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/quote");
            Policy[] policies = Policy.values();
            for (int round = -1; round <= 7; round++) {
                for (Scenario scenario : Scenario.values()) {
                    for (int i = 0; i < policies.length; i++) {
                        Policy policy = policies[Math.floorMod(i + round, policies.length)];
                        carrier.reset(scenario);
                        long start = System.nanoTime();
                        int outcome = call(policy, uri);
                        long elapsed = System.nanoTime() - start;
                        List<Attempt> attempts = carrier.snapshot();
                        checkOutcome(scenario, policy, attempts, outcome);
                        if (round > 0) {
                            String arrivals = attempts.stream().map(a -> millis(a.offsetNanos()))
                                    .collect(Collectors.joining("|"));
                            String statuses = attempts.stream().map(a -> Integer.toString(a.status()))
                                    .collect(Collectors.joining("|"));
                            String fault = scenario == Scenario.timed_outage ? "outage_" + recoveryMs + "ms" : scenario.name();
                            rows.add(String.join(",", Integer.toString(round), fault, policy.name(),
                                    Integer.toString(attempts.size()), Integer.toString(outcome), millis(elapsed),
                                    arrivals, statuses));
                        }
                    }
                }
            }
        } finally {
            server.stop(0);
        }
        Files.write(Path.of("target", "operations.csv"), rows);
    }

    private int call(Policy policy, URI uri) throws IOException, InterruptedException {
        try {
            return switch (policy) {
                case once -> client.once(uri);
                case immediate -> client.immediate(uri);
                case fixed -> client.fixed(uri);
                case exponential -> client.exponential(uri);
                case fibonacci -> client.fibonacci(uri);
            };
        } catch (QuoteClient.CarrierUnavailable exhausted) {
            return 503;
        }
    }

    private static void checkOutcome(Scenario scenario, Policy policy, List<Attempt> attempts, int outcome) {
        assertTrue(attempts.size() >= 1 && attempts.size() <= 4);
        assertEquals(attempts.getLast().status(), outcome);
        if (policy == Policy.once) {
            assertEquals(1, attempts.size());
            assertEquals(503, outcome);
        } else if (scenario == Scenario.first_two_503) {
            assertEquals(3, attempts.size());
            assertEquals(200, outcome);
        } else if (scenario == Scenario.always_503) {
            assertEquals(4, attempts.size());
            assertEquals(503, outcome);
        }
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
