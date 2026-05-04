package dev.topology.api;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@QuarkusTest
class TopologyAndRunsIT {

    @TestHTTPResource("/events")
    URI eventsEndpoint;

    @Test
    void topologyReturnsHtmlBeforeAnyRun() {
        String html = RestAssured.given()
                .when()
                .get("/topology")
                .then()
                .statusCode(200)
                .contentType(ContentType.HTML)
                .extract()
                .body()
                .asString();
        assertTrue(html.toLowerCase().contains("<html") || html.toLowerCase().contains("<!doctype html"),
                "Expected HTML document markers");
    }

    @Test
    void runPipelineThenTopologyShowsExecution() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"request\":\"payment webhook retries exhausting DLQ budget\"}")
                .when()
                .post("/runs")
                .then()
                .statusCode(200)
                .body("summary", equalTo(""));

        RestAssured.given()
                .when()
                .get("/topology")
                .then()
                .statusCode(200)
                .body(containsString("triagePipeline"))
                .body(containsString("stub specialist output"));
    }

    @Test
    void eventsStreamRegistersAndReturnsInitialBytesAfterRun() throws Exception {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"request\":\"SSE smoke\"}")
                .post("/runs");

        HttpURLConnection connection = (HttpURLConnection) eventsEndpoint.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setReadTimeout(5_000);
        connection.connect();
        assertTrue(connection.getContentType() != null && connection.getContentType().contains("text/event-stream"),
                "Expected text/event-stream");

        try (InputStream in = connection.getInputStream()) {
            byte[] buf = new byte[16_384];
            int n = in.read(buf);
            assertTrue(n > 0, "Expected initial SSE bytes");
        } finally {
            connection.disconnect();
        }
    }
}
