package com.themainthread.http3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ProtocolResourceTest {

    @TestHTTPResource(value = "/protocol", tls = true)
    URI endpoint;

    @Test
    void servesHttp11AndAdvertisesHttp3() throws Exception {
        HttpResponse<String> response = verifyProtocol(HttpClient.Version.HTTP_1_1, "HTTP_1_1");
        assertEquals("h3=\":" + endpoint.getPort() + "\"; ma=86400",
                response.headers().firstValue("alt-svc").orElseThrow());
    }

    @Test
    void servesHttp2() throws Exception {
        verifyProtocol(HttpClient.Version.HTTP_2, "HTTP_2");
    }

    @Test
    @EnabledIfSystemProperty(named = "http3.curl", matches = ".+")
    void servesHttp3WithoutFallback() throws Exception {
        Process curl = new ProcessBuilder(System.getProperty("http3.curl"),
                "--insecure", "--silent", "--show-error", "--fail", "--http3-only",
                "--max-time", "10", endpoint.toString())
                .redirectErrorStream(true)
                .start();
        try {
            assertTrue(curl.waitFor(15, TimeUnit.SECONDS), "HTTP/3 curl did not finish");
            String output = new String(curl.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, curl.exitValue(), output);
            assertEquals("HTTP_3", output);
        } finally {
            curl.destroyForcibly();
        }
    }

    private HttpResponse<String> verifyProtocol(HttpClient.Version version, String body) throws Exception {
        // Trust only for local test certificates. Never use this in application code.
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(null, new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } }, null);

        try (HttpClient client = HttpClient.newBuilder()
                .sslContext(tls)
                .version(version)
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals(version, response.version());
            assertEquals(body, response.body());
            return response;
        }
    }
}
