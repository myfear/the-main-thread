package dev.themainthread.invoicerecon.mcp.support;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.Snapshot;
import io.vertx.core.json.JsonObject;

public final class ElicitationTestClient implements AutoCloseable {

    private final McpStreamableTestClient client;
    private final boolean acceptDefaults;

    private ElicitationTestClient(McpStreamableTestClient client, boolean acceptDefaults) {
        this.client = client;
        this.acceptDefaults = acceptDefaults;
    }

    public static ElicitationTestClient acceptDefaults() {
        return new ElicitationTestClient(connect(), true);
    }

    public static ElicitationTestClient declineDefaults() {
        return new ElicitationTestClient(connect(), false);
    }

    public JsonObject reconcileAcmeMay() {
        AtomicReference<JsonObject> responseBody = new AtomicReference<>();

        Thread toolCall = new Thread(() -> client.when()
                .toolsCall(
                        "reconcile_invoices",
                        Map.of(
                                "supplierId", "ACME",
                                "from", LocalDate.of(2026, 5, 1).toString(),
                                "to", LocalDate.of(2026, 5, 31).toString()),
                        response -> responseBody.set(new JsonObject(response.firstContent().asText().text())))
                .thenAssertResults());

        toolCall.start();

        Snapshot requests = client.waitForRequests(1);
        JsonObject elicitationRequest = requests.requests().get(0);
        respondToElicitation(elicitationRequest);

        try {
            toolCall.join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }

        return responseBody.get();
    }

    private void respondToElicitation(JsonObject request) {
        JsonObject params = request.getJsonObject("params");
        JsonObject response = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", request.getValue("id"));

        if (acceptDefaults) {
            JsonObject content = new JsonObject()
                    .put("maximumVariancePercent", params.getJsonObject("requestedSchema")
                            .getJsonObject("properties")
                            .getJsonObject("maximumVariancePercent")
                            .getDouble("default"))
                    .put("defaultCostCenter", "FIN-OPERATIONS")
                    .put("postMatchedInvoices", false)
                    .put("missingGoodsReceiptAction", "FLAG_FOR_REVIEW");
            response.put("result", new JsonObject()
                    .put("action", "accept")
                    .put("content", content));
        } else {
            response.put("result", new JsonObject().put("action", "decline"));
        }

        client.sendAndForget(response);
    }

    private static McpStreamableTestClient connect() {
        return McpAssured.newStreamableClient()
                .setMcpPath("/mcp")
                .setClientCapabilities(new ClientCapability(ClientCapability.ELICITATION, Map.of()))
                .build()
                .connect();
    }

    @Override
    public void close() {
        client.disconnect();
    }
}
