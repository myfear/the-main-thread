package dev.mainthread;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

class HelloResourceIT {

    @Test
    void returnsGreeting() throws Exception {
        String port = System.getProperty("liberty.http.port");
        if (port == null) {
            throw new IllegalStateException("The Liberty Maven plugin did not provide liberty.http.port");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/hello"))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("Liberty is running.", response.body());
    }
}
