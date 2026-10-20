package com.themainthread.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.common.http.TestHTTPResource;

abstract class CalendarContract {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_REQUEST = """
            {"at":"2026-12-24T12:00:00Z","zone":"Europe/Berlin","mode":"explore","variant":"default"}
            """;

    @TestHTTPResource
    URI baseUri;

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24 })
    void eachDoorRunsItsRealScenario(int door) throws Exception {
        JsonNode result = post("/api/doors/" + door + "/run", DEFAULT_REQUEST, 200);
        assertEquals(door, result.at("/lesson/id").asInt());
        assertFalse(result.at("/lesson/code").asText().isBlank());
        assertFalse(result.at("/result/takeaway").asText().isBlank());
        Map<String, String> values = observations(result);
        switch (door) {
            case 1 -> assertEquals("2026-12-23", values.get("Date after converting that instant"));
            case 2 -> {
                assertEquals("2026-12-24T08:00:00Z", values.get("Resolved in Berlin"));
                assertEquals("2026-12-24T00:00:00Z", values.get("Resolved in Tokyo"));
            }
            case 3 -> assertEquals("true", values.get("Same instant"));
            case 4 -> assertEquals("2026-12-24", values.get("Your chosen calendar date"));
            case 5 -> assertEquals("2026-12-24T14:00:00Z", values.get("Rescheduled instant"));
            case 6 -> {
                assertEquals("false", values.get("equals"));
                assertEquals("true", values.get("isEqual"));
            }
            case 7 -> {
                assertEquals("[]", values.get("Valid offsets"));
                assertEquals("2026-03-29T03:30+02:00[Europe/Berlin]", values.get("Java atZone result"));
            }
            case 8 -> {
                assertEquals("2026-10-25T00:30:00Z", values.get("Earlier instant"));
                assertEquals("2026-10-25T01:30:00Z", values.get("Later instant"));
            }
            case 9 -> assertEquals("23", values.get("Elapsed hours for calendar day"));
            case 10 -> assertEquals("2018-11-04T01:00-02:00[America/Sao_Paulo]", values.get("Earliest valid start"));
            case 11 -> {
                assertEquals("2026-03-28", values.get("Repeated plusMonths"));
                assertEquals("2026-03-31", values.get("Two months from anchor"));
            }
            case 12 -> assertEquals("2025-03-01", values.get("Explicit March policy"));
            case 13 -> assertEquals("2020-12-30", values.get("YYYY-MM-dd"));
            case 14 -> {
                assertEquals("2026-02-28", values.get("SMART result"));
                assertEquals("REJECTED: date does not exist", values.get("STRICT result"));
            }
            case 15 -> assertEquals("2026-11-12", values.get("dd/MM/uuuu"));
            case 16 -> assertEquals("123457000", values.get("Stored nanoseconds"));
            case 17 -> {
                assertEquals("23", values.get("Window hours"));
                assertEquals("false", values.get("Next day's start included"));
            }
            case 18 -> assertEquals("2026-12-29", values.get("Three fixture business days later"));
            case 19 -> {
                assertEquals("2030-12-24T07:00:00Z", values.get("Preserve local appointment"));
                assertEquals("2030-12-24T10:00", values.get("Preserve old instant: new wall time"));
            }
            case 20 -> {
                assertEquals("false", values.get("One nanosecond before"));
                assertEquals("true", values.get("Exactly at unlock"));
                assertEquals("true", values.get("One nanosecond after"));
            }
            case 21 -> {
                assertEquals("PT-2S", values.get("Misleading wall-clock duration"));
                assertTrue(Long.parseLong(values.get("Actual two-read nanoTime difference (ns)")) >= 0);
            }
            case 22 -> assertEquals("2026-12-24T09:00+01:00[Europe/Berlin]", values.get("Reconstructed view"));
            case 23 -> assertEquals("reply, request", values.get("Timestamp sort"));
            case 24 -> {
                assertEquals("0", values.get("Committed effects after rollback"));
                assertEquals("1", values.get("Recovery: newly committed effects"));
                assertEquals("0", values.get("Repeated delivery: new effects"));
                assertEquals("1", values.get("Final committed effects"));
                assertEquals(1, get("/api/jobs/" + values.get("Job ID (persisted)"), 200).get("committedEffects").asInt());
            }
            default -> throw new AssertionError("No contract for door " + door);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 21, 22, 23, 24 })
    void alternativeScenariosAreExecutable(int door) throws Exception {
        var result = post("/api/doors/" + door + "/run",
                DEFAULT_REQUEST.replace("\"default\"", "\"alternative\""), 200);
        assertEquals("alternative", result.get("variant").asText());
        var values = observations(result);
        assertTrue(values.size() >= 3);
        switch (door) {
            case 3 -> assertEquals("false", values.get("Same instant"));
            case 7 -> assertEquals("2026-03-08T03:30-04:00[America/New_York]", values.get("Java atZone result"));
            case 8 -> assertEquals("PT1H", values.get("Distance between occurrences"));
            case 9 -> assertEquals("25", values.get("Elapsed hours for calendar day"));
            case 10 -> assertEquals("false", values.get("Requested date survived"));
            case 12 -> assertEquals("2028-02-29", values.get("Explicit March policy"));
            case 13 -> assertEquals("2020-01-01", values.get("YYYY-MM-dd"));
            case 16 -> assertEquals("123000000", values.get("Stored nanoseconds"));
            case 17 -> assertEquals("25", values.get("Window hours"));
            case 18 -> assertEquals("2026-12-30", values.get("Three fixture business days later"));
            case 22 -> assertEquals("Asia/Tokyo", values.get("Separately saved region"));
            case 24 -> assertEquals("1", values.get("Final committed effects"));
            default -> assertFalse(result.at("/result/takeaway").asText().isBlank());
        }
    }

    @Test
    void replayIsEnforcedByTheServerAtTheExactInstant() throws Exception {
        String before = """
                {"at":"2026-12-23T22:59:59.999999999Z","zone":"Europe/Berlin","mode":"replay"}
                """;
        assertEquals(23, countAvailable(calendar("2026-12-23T22:59:59.999999999Z", "Europe/Berlin", "replay")));
        post("/api/doors/24/run", before, 403);
        assertEquals(24, countAvailable(calendar("2026-12-23T23:00:00Z", "Europe/Berlin", "replay")));
        post("/api/doors/24/run", before.replace("22:59:59.999999999", "23:00:00"), 200);
        assertEquals(0, countAvailable(calendar("2026-11-30T22:59:59Z", "Europe/Berlin", "replay")));
        assertEquals(24, countAvailable(calendar("2027-01-01T00:00:00Z", "Europe/Berlin", "replay")));
        assertEquals(24, countAvailable(calendar("2025-01-01T00:00:00Z", "Europe/Berlin", "explore")));
    }

    @Test
    void clocksAreIndependentAcrossInterleavedRequests() throws Exception {
        assertEquals(20, countAvailable(calendar("2026-12-20T00:30:00Z", "Europe/Berlin", "replay")));
        assertEquals(19, countAvailable(calendar("2026-12-20T00:30:00Z", "Pacific/Honolulu", "replay")));
        assertEquals(20, countAvailable(calendar("2026-12-20T00:30:00Z", "Europe/Berlin", "replay")));
    }

    @Test
    void advancingACalendarDayPreservesWallTimeAcrossDst() throws Exception {
        var result = post("/api/clock/advance", """
                {"at":"2026-03-28T11:00:00Z","zone":"Europe/Berlin","mode":"replay"}
                """, 200);
        assertEquals("2026-03-29T10:00:00Z", result.get("instant").asText());
    }

    @Test
    void invalidInputsHaveActionableErrors() throws Exception {
        for (String replacement : new String[] { "not-an-instant", "2026-12-24T12:00:00", "2200-01-01T00:00:00Z" }) {
            assertTrue(post("/api/doors/7/run", DEFAULT_REQUEST.replace("2026-12-24T12:00:00Z", replacement), 400)
                    .get("message").asText().length() > 10);
        }
        post("/api/doors/7/run", DEFAULT_REQUEST.replace("Europe/Berlin", "Mars/Olympus"), 400);
        post("/api/doors/7/run", DEFAULT_REQUEST.replace("explore", "unknown"), 400);
        post("/api/doors/7/run", "null", 400);
        post("/api/doors/0/run", DEFAULT_REQUEST, 404);
        post("/api/doors/25/run", DEFAULT_REQUEST, 404);
        post("/api/doors/20/run", DEFAULT_REQUEST.replace("\"default\"", "\"alternative\""), 400);
        get("/api/jobs/not-a-uuid", 400);
    }

    private JsonNode calendar(String at, String zone, String mode) throws Exception {
        return get("/api/calendar?at=" + URLEncoder.encode(at, StandardCharsets.UTF_8)
                + "&zone=" + URLEncoder.encode(zone, StandardCharsets.UTF_8) + "&mode=" + mode, 200);
    }

    private int countAvailable(JsonNode calendar) {
        int count = 0;
        for (JsonNode door : calendar.get("doors")) {
            if (door.get("available").asBoolean()) {
                count++;
            }
        }
        return count;
    }

    private Map<String, String> observations(JsonNode response) {
        var values = new HashMap<String, String>();
        for (JsonNode observation : response.at("/result/observations")) {
            values.put(observation.get("label").asText(), observation.get("value").asText());
        }
        return values;
    }

    private JsonNode get(String path, int expectedStatus) throws Exception {
        return send(HttpRequest.newBuilder(baseUri.resolve(path)).GET().build(), expectedStatus);
    }

    private JsonNode post(String path, String body, int expectedStatus) throws Exception {
        return send(HttpRequest.newBuilder(baseUri.resolve(path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                expectedStatus);
    }

    private JsonNode send(HttpRequest request, int expectedStatus) throws Exception {
        var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }
}
