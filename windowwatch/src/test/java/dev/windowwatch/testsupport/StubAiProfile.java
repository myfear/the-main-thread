package dev.windowwatch.testsupport;

import java.util.Set;

import io.quarkus.test.junit.QuarkusTestProfile;

public class StubAiProfile implements QuarkusTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(StubWindowWatchAssistant.class, TestTokenCountEstimator.class);
    }
}
