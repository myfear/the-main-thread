package com.requestwatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;

@QuarkusTest
@TestHTTPEndpoint(RequestWatchResource.class)
class RequestWatchResourceTest {

    @TestHTTPResource
    URI baseUri;

    @Test
    void fastEndpointReturnsCachedQuote() {
        RestAssured.get("fast")
                .then()
                .statusCode(200)
                .body("endpoint", org.hamcrest.Matchers.is("fast"))
                .body("priceCents", org.hamcrest.Matchers.is(1299));
    }

    @Test
    void allocatingEndpointReportsTemporaryBuffers() {
        RestAssured.get("allocating")
                .then()
                .statusCode(200)
                .body("endpoint", org.hamcrest.Matchers.is("allocating"))
                .body("bufferCount", org.hamcrest.Matchers.is(768))
                .body("bufferSizeBytes", org.hamcrest.Matchers.is(8192))
                .body("allocatedBytes", org.hamcrest.Matchers.is(6_291_456));
    }

    @Test
    void fixedEndpointAvoidsSerializedLatency() throws Exception {
        List<Long> blockingTimes = sampleElapsedTimes("blocking");
        List<Long> fixedTimes = sampleElapsedTimes("blocking-fixed");

        long slowestBlocking = blockingTimes.stream().mapToLong(Long::longValue).max().orElseThrow();
        long slowestFixed = fixedTimes.stream().mapToLong(Long::longValue).max().orElseThrow();

        assertTrue(slowestBlocking >= 500, "blocking endpoint should serialize parallel requests");
        assertTrue(slowestFixed < 350, "fixed endpoint should avoid serialized latency");
        assertTrue(slowestBlocking - slowestFixed >= 200, "fixed endpoint should be meaningfully faster");
    }

    private List<Long> sampleElapsedTimes(String path) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .executor(executor)
                    .build();

            URI requestUri = URI.create(baseUri.toString() + "/" + path);
            List<CompletableFuture<Long>> futures = IntStream.range(0, 4)
                    .mapToObj(ignored -> send(client, requestUri))
                    .toList();

            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    private CompletableFuture<Long> send(HttpClient client, URI requestUri) {
        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    assertEquals(200, response.statusCode());
                    return JsonPath.from(response.body()).getLong("elapsedMs");
                });
    }
}
