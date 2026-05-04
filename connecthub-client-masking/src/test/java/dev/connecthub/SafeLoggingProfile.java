package dev.connecthub;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class SafeLoggingProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.rest-client.logging.scope", "request-response",
                "quarkus.rest-client.logging.body-limit", "80",
                "quarkus.rest-client.logging.masked-headers", "Authorization,Cookie,X-ConnectHub-Signature");
    }
}
