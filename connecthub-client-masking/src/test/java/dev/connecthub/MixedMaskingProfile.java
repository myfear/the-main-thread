package dev.connecthub;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Global masking omits the custom header (so it would leak on clients that inherit only globals).
 * The notify REST client adds {@code X-ConnectHub-Signature} back on its own logging config.
 */
public class MixedMaskingProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.rest-client.logging.scope", "request-response",
                "quarkus.rest-client.logging.body-limit", "80",
                "quarkus.rest-client.logging.masked-headers", "Authorization,Cookie",
                "quarkus.rest-client.notify-api.logging.masked-headers", "Authorization,Cookie,X-ConnectHub-Signature");
    }
}
