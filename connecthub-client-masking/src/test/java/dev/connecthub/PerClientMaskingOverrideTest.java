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
@TestProfile(MixedMaskingProfile.class)
class PerClientMaskingOverrideTest {

    @TestHTTPResource
    URL baseUrl;

    @Test
    void paymentsClientInheritsGlobalMaskingAndLeaksCustomHeader() {
        try (ClientLogCapture capture = new ClientLogCapture()) {
            RestAssured.when().get(baseUrl + "connect/hooks/payments-only").then().statusCode(200).body(is("payments-ok"));
            String log = capture.captured();
            assertTrue(log.contains(DemoTokens.SIGNATURE),
                    "Global masked-headers omit X-ConnectHub-Signature, so the payments client logs it in plain text");
            assertFalse(log.contains(DemoTokens.BEARER), "Authorization should still be masked globally");
        }
    }

    @Test
    void notifyClientExtendsMaskingForCustomHeader() {
        try (ClientLogCapture capture = new ClientLogCapture()) {
            RestAssured.when().get(baseUrl + "connect/hooks/notify-only").then().statusCode(200).body(is("notify-ok"));
            String log = capture.captured();
            assertFalse(log.contains(DemoTokens.SIGNATURE),
                    "notify-api adds X-ConnectHub-Signature to its per-client masked-headers list");
            assertFalse(log.contains(DemoTokens.SESSION), "Cookie should stay masked");
        }
    }
}
