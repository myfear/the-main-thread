package dev.themainthread.checkout;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class FakeConsulCatalogRegistry implements QuarkusTestResourceLifecycleManager {

    private static final String SERVICE_NAME = "catalog-service";
    private static final String CONSUL_PATH = "/v1/health/service/" + SERVICE_NAME;
    private static final AtomicReference<List<CatalogInstance>> ACTIVE_INSTANCES = new AtomicReference<>(List.of());
    private static final AtomicLong CONSUL_INDEX = new AtomicLong(1);

    private static HttpServer consulServer;
    private static HttpServer catalogOneServer;
    private static HttpServer catalogTwoServer;
    private static CatalogInstance catalogOne;
    private static CatalogInstance catalogTwo;

    @Override
    public Map<String, String> start() {
        try {
            CatalogServer first = startCatalogServer("catalog-1", "blue");
            CatalogServer second = startCatalogServer("catalog-2", "green");
            catalogOne = first.instance();
            catalogTwo = second.instance();
            catalogOneServer = first.server();
            catalogTwoServer = second.server();
            exposeBothInstances();

            consulServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            consulServer.createContext(CONSUL_PATH, this::handleHealthServiceNodes);
            consulServer.start();

            return Map.of(
                    "quarkus.stork.catalog-service.service-discovery.consul-host", "127.0.0.1",
                    "quarkus.stork.catalog-service.service-discovery.consul-port",
                    Integer.toString(consulServer.getAddress().getPort()),
                    "quarkus.stork.catalog-service.service-discovery.refresh-period", "1S");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to start fake Consul registry", e);
        }
    }

    @Override
    public void stop() {
        stopServer(consulServer);
        stopServer(catalogOneServer);
        stopServer(catalogTwoServer);
        ACTIVE_INSTANCES.set(List.of());
        CONSUL_INDEX.set(1);
        consulServer = null;
        catalogOneServer = null;
        catalogTwoServer = null;
        catalogOne = null;
        catalogTwo = null;
    }

    static void exposeBothInstances() {
        ACTIVE_INSTANCES.set(List.of(catalogOne, catalogTwo));
        CONSUL_INDEX.incrementAndGet();
    }

    static void exposeOnlyCatalogOne() {
        ACTIVE_INSTANCES.set(List.of(catalogOne));
        CONSUL_INDEX.incrementAndGet();
    }

    private static CatalogServer startCatalogServer(String instanceId, String color) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CatalogInstance instance = new CatalogInstance(instanceId, color, server.getAddress().getPort());
        server.createContext("/catalog", exchange -> handleCatalogRequest(exchange, instance));
        server.start();
        return new CatalogServer(server, instance);
    }

    private void handleHealthServiceNodes(HttpExchange exchange) throws IOException {
        byte[] payload = toConsulServiceEntries(ACTIVE_INSTANCES.get()).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("X-Consul-Index", Long.toString(CONSUL_INDEX.get()));
        exchange.getResponseHeaders().set("X-Consul-KnownLeader", "true");
        exchange.getResponseHeaders().set("X-Consul-LastContact", "0");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(payload);
        }
    }

    private static void handleCatalogRequest(HttpExchange exchange, CatalogInstance instance) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!"GET".equals(exchange.getRequestMethod()) || !path.startsWith("/catalog/")) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        String sku = path.substring("/catalog/".length());
        String payload = """
                {
                  "sku": "%s",
                  "price": 19.99,
                  "instanceId": "%s",
                  "color": "%s",
                  "servedAt": "%s"
                }
                """.formatted(sku, instance.id(), instance.color(), Instant.parse("2026-06-09T08:00:00Z"));

        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private static String toConsulServiceEntries(List<CatalogInstance> instances) {
        return instances.stream()
                .map(instance -> """
                        {
                          "Node": {
                            "ID": "%s-node-id",
                            "Node": "%s-node",
                            "Address": "127.0.0.1",
                            "Datacenter": "dc1"
                          },
                          "Service": {
                            "ID": "%s",
                            "Service": "%s",
                            "Address": "127.0.0.1",
                            "Port": %d,
                            "Tags": ["color=%s"]
                          },
                          "Checks": [
                            {
                              "CheckID": "service:%s",
                              "Name": "Service '%s' check",
                              "Node": "%s-node",
                              "Status": "passing",
                              "Notes": "",
                              "Output": "HTTP GET http://127.0.0.1:%d/q/health/live: 200 OK",
                              "ServiceID": "%s",
                              "ServiceName": "%s"
                            }
                          ]
                        }
                        """.formatted(
                        instance.id(),
                        instance.id(),
                        instance.id(),
                        SERVICE_NAME,
                        instance.port(),
                        instance.color(),
                        instance.id(),
                        SERVICE_NAME,
                        instance.id(),
                        instance.port(),
                        instance.id(),
                        SERVICE_NAME))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static void stopServer(HttpServer server) {
        if (server != null) {
            server.stop(0);
        }
    }

    private record CatalogServer(HttpServer server, CatalogInstance instance) {
    }

    private record CatalogInstance(String id, String color, int port) {
    }
}
