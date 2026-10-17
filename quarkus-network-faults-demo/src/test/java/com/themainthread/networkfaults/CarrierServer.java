package com.themainthread.networkfaults;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;

public class CarrierServer implements QuarkusTestResourceLifecycleManager {

    private Vertx vertx;
    private HttpServer server;

    @Override
    public Map<String, String> start() {
        vertx = Vertx.vertx();
        server = vertx.createHttpServer().requestHandler(request -> {
            if (!request.path().equals("/carrier/quotes")) {
                request.response().setStatusCode(404).end();
                return;
            }
            String destination = request.getParam("destination");
            String body = new JsonObject()
                    .put("destination", destination)
                    .put("amount", 9.90)
                    .put("currency", "EUR")
                    .encode();
            request.response().putHeader("Content-Type", "application/json").end(body);
        }).listen(0, "127.0.0.1").await();
        return Map.of("carrier.url", "http://127.0.0.1:" + server.actualPort());
    }

    @Override
    public void stop() {
        if (server != null) {
            server.close().await();
        }
        if (vertx != null) {
            vertx.close().await();
        }
    }
}
