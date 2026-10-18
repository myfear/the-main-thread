package com.themainthread.gifts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(DemoDisabledTest.Disabled.class)
class DemoDisabledTest {
    public static class Disabled implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("gift.demo.enabled", "false");
        }
    }

    @TestHTTPResource
    URI base;

    @Test
    void demoRoutesAreDisabledButNormalCreationIsAvailable() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String path : new String[] { "/api/demo/rounds",
                    "/api/demo/rounds/" + UUID.randomUUID() + "/unsafe/reservations",
                    "/api/demo/rounds/" + UUID.randomUUID() + "/optimistic/reservations" }) {
                HttpRequest request = HttpRequest.newBuilder(base.resolve(path)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"buyer\":\"First grandparent\"}")).build();
                assertEquals(404, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            HttpRequest get = HttpRequest.newBuilder(base.resolve("/api/demo/rounds/" + UUID.randomUUID())).GET().build();
            assertEquals(404, client.send(get, HttpResponse.BodyHandlers.ofString()).statusCode());
            HttpRequest create = HttpRequest.newBuilder(base.resolve("/api/gifts")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Board game\"}")).build();
            assertEquals(201, client.send(create, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }
}
