package com.themainthread.gluhwein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.common.http.TestHTTPResource;

abstract class OrderContract {
    @TestHTTPResource
    URI base;

    private final ObjectMapper json = new ObjectMapper();

    private HttpRequest.Builder at(String path) {
        return HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10));
    }

    private HttpResponse<String> post(HttpClient client, String path, String body) throws Exception {
        return client.send(at(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void wireStreamRecoversAfterDisconnectAndHonorsOrderIsolation() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            var created = post(client, "/api/orders", """
                    {"name":"Mia","drink":"RED","quantity":2}
                    """);
            assertEquals(201, created.statusCode());
            String path = created.headers().firstValue("Location").orElseThrow();
            JsonNode order = json.readTree(created.body());
            assertEquals("QUEUED", order.get("status").asText());
            try (SseReader stream = stream(client, path + "/events", "")) {
                assertTrue(stream.next().contains("\"QUEUED\""));
                assertEquals(200, post(client, path + "/advance", "{\"revision\":1}").statusCode());
                String preparing = stream.next();
                assertTrue(preparing.contains("id:2"), preparing);
                assertTrue(preparing.contains("\"PREPARING\""), preparing);
            }
            assertEquals(200, post(client, path + "/advance", "{\"revision\":2}").statusCode());
            var other = post(client, "/api/orders", "{\"name\":\"Noah\",\"drink\":\"APPLE\",\"quantity\":1}");
            assertEquals(201, other.statusCode());
            try (SseReader reconnect = stream(client, path + "/events", "2")) {
                String ready = reconnect.next();
                assertTrue(ready.contains("event:order"), ready);
                assertTrue(ready.contains("id:3"), ready);
                assertTrue(ready.contains("\"READY\""), ready);
                assertTrue(ready.contains(order.get("id").asText()), ready);
            }
            assertEquals(409, post(client, path + "/advance", "{\"revision\":2}").statusCode());
            assertEquals(409, post(client, path + "/advance", "{\"revision\":3}").statusCode());
        }
    }

    @Test
    void validationAndMissingTicketsReturnHttpErrors() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String body : new String[] { "null", "{}",
                    "{\"name\":\"   \",\"drink\":\"RED\",\"quantity\":1}",
                    "{\"name\":\"Mia\",\"drink\":\"UNKNOWN\",\"quantity\":1}",
                    "{\"name\":\"Mia\",\"drink\":\"RED\",\"quantity\":7}" }) {
                assertEquals(400, post(client, "/api/orders", body).statusCode(), body);
            }
            var missing = client.send(at("/api/orders/" + UUID.randomUUID() + "/events").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, missing.statusCode());
            assertTrue(missing.body().contains("no longer available"));
        }
    }

    @Test
    void counterReceivesOrdersAndBrowserAssetsArePackaged() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient(); SseReader board = stream(client, "/api/orders/events", "")) {
            assertTrue(board.next().contains("event:board"));
            var created = post(client, "/api/orders", "{\"name\":\"Lin\",\"drink\":\"WHITE\",\"quantity\":3}");
            String id = json.readTree(created.body()).get("id").asText();
            assertTrue(board.next().contains(id));
            for (String asset : new String[] { "/", "/counter.html", "/styles.css", "/app.js", "/counter.js" }) {
                assertEquals(200, client.send(at(asset).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode(), asset);
            }
        }
    }

    private SseReader stream(HttpClient client, String path, String lastId) throws Exception {
        var response = client.send(at(path).header("Last-Event-ID", lastId).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/event-stream"));
        return new SseReader(response.body());
    }

    private static class SseReader implements AutoCloseable {
        private final InputStream input;
        private final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
        private final Thread reader;

        SseReader(InputStream input) {
            this.input = input;
            reader = Thread.ofVirtual().start(() -> {
                try (var lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    var event = new StringBuilder();
                    String line;
                    while ((line = lines.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (event.toString().contains("data:")) {
                                events.add(event.toString());
                            }
                            event.setLength(0);
                        } else {
                            event.append(line).append('\n');
                        }
                    }
                } catch (Exception ignored) {
                    // Closing a streaming HTTP body interrupts this read.
                }
            });
        }

        String next() throws InterruptedException {
            String event = events.poll(5, TimeUnit.SECONDS);
            assertTrue(event != null, "Expected an SSE data event within five seconds");
            return event;
        }

        @Override
        public void close() throws Exception {
            input.close();
            reader.join(1000);
        }
    }
}
