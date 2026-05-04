package dev.connecthub;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

import java.net.URL;

@QuarkusTest
@TestProfile(SafeLoggingProfile.class)
class MaskedRestClientLoggingTest {

    @TestHTTPResource
    URL baseUrl;

    @Test
    void sensitiveHeadersDoNotAppearInClientLogs() {
        try (ClientLogCapture capture = new ClientLogCapture()) {
            RestAssured.when().get(baseUrl + "connect/hooks/demo").then().statusCode(200).body(is("ok"));
            String log = capture.captured();
            assertFalse(log.contains(DemoTokens.BEARER), "Bearer token should not appear when masked");
            assertFalse(log.contains(DemoTokens.SESSION), "Session value should not appear when Cookie is masked");
            assertFalse(log.contains(DemoTokens.SIGNATURE), "Signature should not appear when masked");
            assertTrue(log.contains("Authorization=") || log.contains("Authorization=****"),
                    "Request logging should still mention the Authorization header");
        }
    }
}
