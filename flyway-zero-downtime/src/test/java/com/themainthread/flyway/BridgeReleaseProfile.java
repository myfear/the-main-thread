package com.themainthread.flyway;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class BridgeReleaseProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "migration-demo.release", "BRIDGE",
                "migration-demo.schema-target", "2",
                "quarkus.flyway.clean-at-start", "true",
                "quarkus.flyway.migrate-at-start", "true");
    }
}
