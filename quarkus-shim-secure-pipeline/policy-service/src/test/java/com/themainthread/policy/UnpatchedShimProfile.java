package com.themainthread.policy;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class UnpatchedShimProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.shim.enabled", "false");
    }
}
