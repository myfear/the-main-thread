package com.catalogapi;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ReflectionFreeProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.rest.jackson.optimization.enable-reflection-free-serializers", "true");
    }
}
