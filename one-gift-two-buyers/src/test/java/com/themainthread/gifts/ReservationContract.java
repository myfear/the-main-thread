package com.themainthread.gifts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.common.http.TestHTTPResource;

/** The same HTTP contract runs in-process and against the packaged application. */
public abstract class ReservationContract {
    @TestHTTPResource
    URI base;

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    @RepeatedTest(5)
    void bothReadAvailableButOnlyVersionedUpdateRejectsTheLoser() throws Exception {
        String id = createRound();
        List<HttpResponse<String>> unsafe = race(id, "unsafe");
        assertEquals(List.of(200, 200), statuses(unsafe));
        assertEquals("First grandparent", body(unsafe.get(0)).get("reservedBy").asText());
        assertEquals("Second grandparent", body(unsafe.get(1)).get("reservedBy").asText());

        List<HttpResponse<String>> optimistic = race(id, "optimistic");
        assertEquals(List.of(200, 409), statuses(optimistic));
        HttpResponse<String> winner = optimistic.stream().filter(r -> r.statusCode() == 200).findFirst().orElseThrow();
        HttpResponse<String> loser = optimistic.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow();
        assertEquals("reservation_conflict", body(loser).get("code").asText());
        assertEquals(id, body(loser).get("giftId").asText());

        JsonNode round = body(send("GET", "/api/demo/rounds/" + id, null));
        assertTrue(List.of("First grandparent", "Second grandparent").contains(round.get("unsafe").get("reservedBy").asText()));
        assertTrue(round.get("unsafe").get("version").isNull());
        assertEquals(body(winner).get("reservedBy"), round.get("optimistic").get("reservedBy"));
        assertEquals(1, round.get("optimistic").get("version").asLong());
        assertEquals(1, body(winner).get("version").asLong());
    }

    @Test
    void ordinaryEndpointCommitsThenRejectsAnAlreadyReservedGift() throws Exception {
        HttpResponse<String> created = send("POST", "/api/gifts", "{\"name\":\"  Board game  \"}");
        assertEquals(201, created.statusCode());
        String id = body(created).get("id").asText();
        assertEquals("Board game", body(created).get("name").asText());
        assertTrue(created.headers().firstValue("Location").orElseThrow().endsWith("/api/gifts/" + id));
        HttpResponse<String> first = send("POST", "/api/gifts/" + id + "/reservations", buyer(" First grandparent "));
        assertEquals(200, first.statusCode());
        assertEquals("First grandparent", body(first).get("reservedBy").asText());
        HttpResponse<String> second = send("POST", "/api/gifts/" + id + "/reservations", buyer("Second grandparent"));
        assertEquals(409, second.statusCode());
        assertEquals("already_reserved", body(second).get("code").asText());
        JsonNode saved = body(send("GET", "/api/gifts/" + id, null));
        assertEquals("First grandparent", saved.get("reservedBy").asText());
        assertEquals(1, saved.get("version").asLong());
    }

    @Test
    void validationAndMissingGiftHaveClientErrors() throws Exception {
        String id = createRound();
        String path = "/api/gifts/" + id + "/reservations";
        for (String invalid : List.of("{}", "null", buyer("   "), buyer("a".repeat(81)))) {
            assertEquals(400, send("POST", path, invalid).statusCode(), invalid);
        }
        assertEquals(400, send("POST", "/api/gifts", "{\"name\":\"\"}").statusCode());
        assertEquals(400, send("POST", "/api/gifts", "null").statusCode());
        assertEquals(400, send("POST", path, "{").statusCode());
        assertEquals(404, send("GET", "/api/gifts/" + UUID.randomUUID(), null).statusCode());
        assertEquals(404, send("POST", "/api/gifts/" + UUID.randomUUID() + "/reservations", buyer("First grandparent")).statusCode());
        assertEquals(404, send("POST", "/api/demo/rounds/" + id + "/missing/reservations", buyer("First grandparent")).statusCode());
        assertTrue(body(send("GET", "/api/gifts/" + id, null)).get("reservedBy").isNull());
    }

    @Test
    void missingSecondBuyerTimesOutAndRollsBackWithoutHoldingOtherGifts() throws Exception {
        String waitingId = createRound();
        String otherId = createRound();
        assertNotEquals(waitingId, otherId);
        CompletableFuture<HttpResponse<String>> waiting = async("POST",
                "/api/demo/rounds/" + waitingId + "/optimistic/reservations", buyer("First grandparent"));
        assertEquals(List.of(200, 409), statuses(race(otherId, "optimistic")));
        HttpResponse<String> timeout = waiting.get(15, TimeUnit.SECONDS);
        assertEquals(503, timeout.statusCode());
        assertEquals("race_incomplete", body(timeout).get("code").asText());
        JsonNode unchanged = body(send("GET", "/api/gifts/" + waitingId, null));
        assertTrue(unchanged.get("reservedBy").isNull());
        assertEquals(0, unchanged.get("version").asLong());
        assertEquals(200, send("POST", "/api/gifts/" + waitingId + "/reservations", buyer("Second grandparent")).statusCode());
    }

    @Test
    void browserAssetsAreServed() throws Exception {
        assertTrue(send("GET", "/", null).body().contains("One gift, two buyers"));
        assertEquals(200, send("GET", "/app.js", null).statusCode());
        assertEquals(200, send("GET", "/styles.css", null).statusCode());
    }

    private String createRound() throws Exception {
        HttpResponse<String> created = send("POST", "/api/demo/rounds", "");
        assertEquals(201, created.statusCode(), created.body());
        JsonNode round = body(created);
        assertTrue(round.get("optimistic").get("reservedBy").isNull());
        assertEquals(0, round.get("optimistic").get("version").asLong());
        return round.get("id").asText();
    }

    private List<HttpResponse<String>> race(String id, String mode) throws Exception {
        String path = "/api/demo/rounds/" + id + "/" + mode + "/reservations";
        CompletableFuture<HttpResponse<String>> first = async("POST", path, buyer("First grandparent"));
        CompletableFuture<HttpResponse<String>> second = async("POST", path, buyer("Second grandparent"));
        return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
    }

    private List<Integer> statuses(List<HttpResponse<String>> responses) {
        return responses.stream().map(HttpResponse::statusCode).sorted().toList();
    }

    private String buyer(String name) throws Exception {
        return JSON.writeValueAsString(new ApiTypes.Reservation(name));
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String content) throws Exception {
        return async(method, path, content).get(15, TimeUnit.SECONDS);
    }

    private CompletableFuture<HttpResponse<String>> async(String method, String path, String content) {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .method(method, content == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(content))
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }
}
