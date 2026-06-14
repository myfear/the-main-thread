package dev.verdictiq.testsupport;

import java.util.Set;

import io.quarkus.test.junit.QuarkusTestProfile;

public class StubAiProfile implements QuarkusTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(StubJudgeAiService.class, StubGranitePanelist.class, StubMistralPanelist.class);
    }
}
