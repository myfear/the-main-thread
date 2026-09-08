package com.themainthread.returns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ReturnContractTest {
    @TestHTTPResource
    URI base;

    @Inject
    ObjectMapper json;

    private HttpResponse<String> preview(String orderId, String body) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(base.resolve("api/orders/" + orderId + "/return-preview"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void returnsExactRefundWithoutSubmittingOrChangingTheOrder() throws Exception {
        String input = """
                {"items":[{"sku":"KEYBOARD","quantity":1},{"sku":"CABLE","quantity":1}],"reason":"damaged"}
                """;
        HttpResponse<String> first = preview("ORD-1042", input);
        assertEquals(200, first.statusCode());
        var result = json.readTree(first.body());
        assertEquals(10800, result.get("refundCents").asInt());
        assertFalse(result.get("submitted").asBoolean());
        assertEquals("DRAFT", result.get("status").asText());
        assertEquals(first.body(), preview("ORD-1042", input).body());
        try (HttpClient client = HttpClient.newHttpClient()) {
            var order = client.send(HttpRequest.newBuilder(base.resolve("api/orders/ORD-1042")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(2, json.readTree(order.body()).get("items").get(1).get("quantity").asInt());
        }
    }

    @Test
    void rejectsQuantitiesOutsideThePurchasedRange() throws Exception {
        for (String quantity : new String[] { "0", "-1", "3", "null" }) {
            var response = preview("ORD-1042", "{\"items\":[{\"sku\":\"CABLE\",\"quantity\":" + quantity
                    + "}],\"reason\":\"damaged\"}");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("between 1 and 2"));
        }
    }

    @Test
    void rejectsNonReturnableUnknownAndDuplicateItems() throws Exception {
        for (String items : new String[] {
                "[{\"sku\":\"GIFT-CARD\",\"quantity\":1}]",
                "[{\"sku\":\"UNKNOWN\",\"quantity\":1}]",
                "[{\"sku\":\"CABLE\",\"quantity\":1},{\"sku\":\"CABLE\",\"quantity\":1}]",
                "[]", "[null]" }) {
            assertEquals(400, preview("ORD-1042", "{\"items\":" + items + ",\"reason\":\"damaged\"}").statusCode());
        }
    }

    @Test
    void rejectsUnknownOrderReasonAndMalformedInput() throws Exception {
        assertEquals(400, preview("MISSING", "{\"items\":[],\"reason\":\"damaged\"}").statusCode());
        assertEquals(400, preview("ORD-1042", "{\"items\":[{\"sku\":\"CABLE\",\"quantity\":1}],\"reason\":\"other\"}").statusCode());
        assertEquals(400, preview("ORD-1042", "null").statusCode());
        assertEquals(400, preview("ORD-1042", "{").statusCode());
    }

    @Test
    void rejectsFractionalQuantitiesRatherThanTruncatingThem() throws Exception {
        assertEquals(400, preview("ORD-1042",
                "{\"items\":[{\"sku\":\"CABLE\",\"quantity\":1.5}],\"reason\":\"damaged\"}").statusCode());
    }

    @Test
    void servesTheHumanPage() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(base).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("ORD-1042"));
            assertTrue(response.body().contains("id=\"return-form\""));
        }
    }
}
