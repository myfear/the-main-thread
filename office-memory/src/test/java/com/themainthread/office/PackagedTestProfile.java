package com.themainthread.office;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class PackagedTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("office.memory.verify-embedding-on-start", "false",
                "quarkus.langchain4j.ollama.base-url", "http://127.0.0.1:1");
    }
}
