package com.themainthread.flyway;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ManualMigrationProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "migration-demo.release", "MODERN",
                "quarkus.flyway.migrate-at-start", "false");
    }
}
