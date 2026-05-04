package dev.connecthub;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Overrides global masked headers with a list that omits {@code Authorization} and {@code Cookie},
 * which demonstrates the footgun described in the REST Client guide: explicit lists replace defaults.
 */
public class LeakyLoggingProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.rest-client.logging.scope", "request-response",
                "quarkus.rest-client.logging.body-limit", "80",
                "quarkus.rest-client.logging.masked-headers", "X-ConnectHub-Signature");
    }
}
