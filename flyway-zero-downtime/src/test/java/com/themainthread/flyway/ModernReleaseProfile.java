package com.themainthread.flyway;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ModernReleaseProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "migration-demo.release", "MODERN",
                "migration-demo.schema-target", "4",
                "quarkus.flyway.clean-at-start", "true",
                "quarkus.flyway.migrate-at-start", "true");
    }
}
