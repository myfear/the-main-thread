package dev.connecthub;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

import java.net.URL;

@QuarkusTest
@TestProfile(LeakyLoggingProfile.class)
class LeakyRestClientLoggingTest {

    @TestHTTPResource
    URL baseUrl;

    @Test
    void partialMaskedHeadersListLeaksAuthorizationAndCookie() {
        try (ClientLogCapture capture = new ClientLogCapture()) {
            RestAssured.when().get(baseUrl + "connect/hooks/payments-only").then().statusCode(200).body(is("payments-ok"));
            String log = capture.captured();
            assertTrue(log.contains(DemoTokens.BEARER),
                    "When Authorization is not in masked-headers, the bearer token is logged in plain text");
        }
    }
}
