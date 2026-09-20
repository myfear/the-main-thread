package com.themainthread.mdc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MdcResourceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TestHTTPResource
    URI baseUri;

    @Test
    void rawTaskLosesContextDespiteCorrectResponseHeader() throws Exception {
        JsonNode body = get("common-pool", "raw-123", 200);
        assertTrue(body.has("requestId"));
        assertTrue(body.get("requestId").isNull());
    }

    @Test
    void managedTaskKeepsContext() throws Exception {
        assertEquals("managed-123", get("managed", "managed-123", 200).get("requestId").asText());
    }

    @Test
    void mutinyKeepsContext() throws Exception {
        assertEquals("mutiny-123", get("mutiny", "mutiny-123", 200).get("requestId").asText());
    }

    @Test
    void uncapturedContinuationLosesContext() throws Exception {
        JsonNode body = get("uncaptured", "raw-123", 200);
        assertTrue(body.get("library").get("requestId").isNull());
        assertTrue(body.get("continuation").get("requestId").isNull());
    }

    @Test
    void threadContextAloneDoesNotRestoreVertxMdc() throws Exception {
        JsonNode body = get("captured", "capture-123", 200);
        assertTrue(body.get("library").get("requestId").isNull());
        assertTrue(body.get("continuation").get("requestId").isNull());
    }

    @Test
    void returningToRequestContextRestoresOnlyTheContinuation() throws Exception {
        JsonNode body = get("bridged", "bridge-123", 200);
        assertTrue(body.get("library").get("requestId").isNull());
        assertEquals("bridge-123", body.get("continuation").get("requestId").asText());
    }

    @Test
    void missingAndInvalidIdsAreReplaced() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            List<String> generated = new ArrayList<>();
            for (String incoming : new String[] { null, "", "has spaces", "x".repeat(129), "one,two" }) {
                HttpResponse<String> response = client.send(request("managed", incoming),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                String id = response.headers().firstValue("X-Request-ID").orElseThrow();
                assertEquals(id, UUID.fromString(id).toString());
                assertEquals(id, JSON.readTree(response.body()).get("requestId").asText());
                assertNotEquals(incoming, id);
                assertTrue(!generated.contains(id));
                generated.add(id);
            }
        }
    }

    @Test
    void acceptsTheMaximumLengthId() throws Exception {
        String id = "A._:-0" + "x".repeat(122);
        assertEquals(id, get("managed", id, 200).get("requestId").asText());
    }

    @Test
    void duplicateHeadersAreReplaced() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("mdc/managed"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(10))
                    .header("X-Request-ID", "first")
                    .header("X-Request-ID", "second").build();
            assertEquals(List.of("first", "second"), request.headers().allValues("X-Request-ID"));
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            String id = response.headers().firstValue("X-Request-ID").orElseThrow();
            assertEquals(id, UUID.fromString(id).toString());
            assertEquals(id, JSON.readTree(response.body()).get("requestId").asText());
        }
    }

    @Test
    void failureKeepsItsIdAndNextRequestGetsAFreshOne() throws Exception {
        assertEquals("failed-123", get("managed-failure", "failed-123", 503).get("requestId").asText());
        assertNotEquals("failed-123", get("managed", null, 200).get("requestId").asText());
    }

    @Test
    void overlappingRequestsKeepTheirOwnIds() throws Exception {
        String[] endpoints = { "common-pool", "managed", "mutiny", "uncaptured", "captured", "bridged", "managed-failure" };
        try (HttpClient client = HttpClient.newHttpClient()) {
            List<CompletableFuture<Void>> checks = new ArrayList<>();
            for (int i = 0; i < 140; i++) {
                String endpoint = endpoints[i % endpoints.length];
                String id = "parallel-" + i;
                checks.add(client.sendAsync(request(endpoint, id), HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            assertEquals(endpoint.equals("managed-failure") ? 503 : 200, response.statusCode());
                            assertEquals(id, response.headers().firstValue("X-Request-ID").orElseThrow());
                            JsonNode body;
                            try {
                                body = JSON.readTree(response.body());
                            } catch (Exception e) {
                                throw new AssertionError("Invalid JSON for " + id, e);
                            }
                            if (endpoint.equals("captured") || endpoint.equals("uncaptured") || endpoint.equals("bridged")) {
                                assertTrue(body.get("library").get("requestId").isNull());
                                body = body.get("continuation");
                            }
                            if (endpoint.equals("common-pool") || endpoint.equals("uncaptured") || endpoint.equals("captured")) {
                                assertTrue(body.get("requestId").isNull());
                            } else {
                                assertEquals(id, body.get("requestId").asText());
                            }
                        }));
                if (checks.size() == 20) {
                    CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
                    checks.clear();
                }
            }
        }
    }

    private JsonNode get(String endpoint, String id, int status) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request(endpoint, id), HttpResponse.BodyHandlers.ofString());
            assertEquals(status, response.statusCode());
            String returnedId = response.headers().firstValue("X-Request-ID").orElseThrow();
            if (id != null) {
                assertEquals(id, returnedId);
            }
            return JSON.readTree(response.body());
        }
    }

    private HttpRequest request(String endpoint, String id) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve("mdc/" + endpoint))
                .timeout(Duration.ofSeconds(10));
        if (id != null) {
            builder.header("X-Request-ID", id);
        }
        return builder.GET().build();
    }
}
