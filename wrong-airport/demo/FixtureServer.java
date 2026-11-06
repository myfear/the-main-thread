// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

/** Serves synthetic RDF/XML to the companion's normal REST Client. */
public class FixtureServer {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9090;
        String scenario = args.length > 1 ? args[1] : "wrong-airport-first";
        Set<String> scenarios = Set.of("wrong-airport-first", "target-airport-first", "missing-target",
                "ambiguous-values", "nested-other-subject", "xxentity");
        if (args.length > 2 || !scenarios.contains(scenario)) {
            throw new IllegalArgumentException("Usage: java demo/FixtureServer.java [port] [scenario]. Scenarios: " + scenarios);
        }
        byte[] body = Files.readAllBytes(Path.of("demo", "rdf", scenario + ".rdf"));
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/data/Munich_Airport.rdf", exchange -> {
            try (exchange) {
                if (!exchange.getRequestMethod().equals("GET")
                        || !exchange.getRequestURI().getPath().equals("/data/Munich_Airport.rdf")) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/rdf+xml; charset=UTF-8");
                exchange.getResponseHeaders().set("X-Demo-Data", "synthetic");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write(body);
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            executor.shutdownNow();
        }));
        server.start();
        // Command-line status, not application request logging.
        System.out.printf("Synthetic RDF: http://127.0.0.1:%d/data/Munich_Airport.rdf (%s)%n", port, scenario);
        new CountDownLatch(1).await();
    }
}
