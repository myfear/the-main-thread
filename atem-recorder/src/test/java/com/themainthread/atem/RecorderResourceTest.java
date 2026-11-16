package com.themainthread.atem;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(MediaMtxStub.class)
class RecorderResourceTest {
    private static final String STATUS = "/api/atem/status";
    private static final String LIBRARY = "/api/atem/recordings";
    private static final String DOWNLOAD = LIBRARY + "/download";
    private static final String START = "2026-11-11T15:00:00.123456789+01:00";

    @BeforeEach
    void reset() {
        MediaMtxStub.reset();
    }

    @Test
    void rendersDashboardWithConfiguredPath() {
        given().get("/").then().statusCode(200)
                .contentType(MediaType.TEXT_HTML)
                .body(containsString("ATEM Recorder"))
                .body(containsString("live/atem"));
    }

    @Test
    void treatsMissingPathAndMissingSegmentsAsIdleAndEmpty() {
        given().get(STATUS).then().statusCode(200)
                .body("online", equalTo(false))
                .body("connectedSince", nullValue());
        given().get(LIBRARY).then().statusCode(200).body("$", hasSize(0));
        assertEquals("path=live%2Fatem", MediaMtxStub.query(MediaMtxStub.LIST));
    }

    @Test
    void readsLiveStateAndNormalizesConnectionTime() {
        MediaMtxStub.json(MediaMtxStub.STATUS, 200, """
                {"online":true,"onlineTime":"2026-11-11T15:00:00.123456789+01:00","ready":false}
                """);
        given().get(STATUS).then().statusCode(200)
                .body("online", equalTo(true))
                .body("connectedSince", equalTo("2026-11-11T14:00:00.123456789Z"));
    }

    @Test
    void sortsRangesAndReplacesUpstreamLinks() {
        MediaMtxStub.json(MediaMtxStub.LIST, 200, """
                [
                  {"start":"2026-11-11T12:00:00Z","duration":4.5,"url":"http://127.0.0.1:9996/get"},
                  {"start":"2026-11-11T15:00:00+01:00","duration":32.25,"url":"http://elsewhere/get"}
                ]
                """);
        given().get(LIBRARY).then().statusCode(200)
                .body("$", hasSize(2))
                .body("[0].start", equalTo("2026-11-11T14:00:00Z"))
                .body("[0].duration", equalTo(32.25f))
                .body("[0].downloadUrl", equalTo(DOWNLOAD + "?start=2026-11-11T14%3A00%3A00Z&duration=32.25"));
    }

    @Test
    void streamsAnAttachmentAndEncodesTheMediaMtxRequest() {
        MediaMtxStub.mp4("sample payload");
        given().queryParam("start", START).queryParam("duration", 32.25)
                .get(DOWNLOAD).then().statusCode(200).contentType("video/mp4")
                .header("Content-Disposition", "attachment; filename=\"atem-2026-11-11T14-00-00.123456789Z.mp4\"")
                .body(equalTo("sample payload"));
        assertEquals("path=live%2Fatem&start=2026-11-11T14%3A00%3A00.123456789Z&duration=32.25&format=mp4",
                MediaMtxStub.query(MediaMtxStub.GET));
    }

    @ParameterizedTest
    @ValueSource(strings = { "NaN", "Infinity", "-1", "0", "seconds" })
    void rejectsInvalidDurationsBeforeContactingMediaMtx(String duration) {
        given().queryParam("start", START).queryParam("duration", duration)
                .get(DOWNLOAD).then().statusCode(400);
        assertNull(MediaMtxStub.query(MediaMtxStub.GET));
    }

    @Test
    void rejectsMissingAndMalformedTimestamps() {
        given().queryParam("duration", 1).get(DOWNLOAD).then().statusCode(400);
        given().queryParam("start", "2026-11-11T15:00:00").queryParam("duration", 1)
                .get(DOWNLOAD).then().statusCode(400);
        assertNull(MediaMtxStub.query(MediaMtxStub.GET));
    }

    @Test
    void reportsFailuresIndependently() {
        MediaMtxStub.json(MediaMtxStub.STATUS, 200, "{\"online\":true,\"onlineTime\":null}");
        MediaMtxStub.json(MediaMtxStub.LIST, 200, "[]");
        given().get(STATUS).then().statusCode(502);
        given().get(LIBRARY).then().statusCode(200);

        MediaMtxStub.json(MediaMtxStub.STATUS, 200, "{\"online\":false}");
        MediaMtxStub.json(MediaMtxStub.LIST, 503, "{}");
        given().get(STATUS).then().statusCode(200).body("online", equalTo(false));
        given().get(LIBRARY).then().statusCode(502);
        MediaMtxStub.json(MediaMtxStub.LIST, 200, "[] malformed");
        given().get(LIBRARY).then().statusCode(502);
    }

    @Test
    void rendersDownloadFailuresForApiClientsAndBrowsers() {
        given().queryParam("start", START).queryParam("duration", 1)
                .get(DOWNLOAD).then().statusCode(404)
                .body("message", containsString("no longer available"));
        given().accept(MediaType.TEXT_HTML).queryParam("start", START).queryParam("duration", 1)
                .get(DOWNLOAD).then().statusCode(404).contentType(MediaType.TEXT_HTML)
                .body(containsString("Download failed"))
                .body(containsString("Back to recordings"));
    }
}
