package com.themainthread.codemode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class FixtureReady {
    private FixtureReady() {
    }

    static void await(int port) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/items"))
                    .timeout(Duration.ofSeconds(2)).build();
            while (System.nanoTime() < deadline) {
                try {
                    if (http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200) {
                        return;
                    }
                } catch (IOException e) {
                    // Dev mode is still starting.
                }
                Thread.sleep(250);
            }
        }
        throw new IOException("Fixture did not become ready before its setup deadline");
    }
}
