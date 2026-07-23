package com.themainthread.releasegate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.Message;
import com.themainthread.releasegate.proto.ReleaseAssessment;
import com.themainthread.releasegate.proto.ReleaseCandidate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskScorerTest {

    private final RiskScorerImpl scorer = new RiskScorerImpl(() -> "unit-test");

    @Test
    void calculatesDeterministicRisk() {
        ReleaseCandidate candidate = ReleaseCandidate.newBuilder()
                .setId("release-42")
                .setChangedFiles(6)
                .setCriticalDependencies(1)
                .build();

        Map<String, Message> result = scorer.score(Map.of(PayloadKeys.CANDIDATE, candidate));

        ReleaseAssessment assessment = (ReleaseAssessment) result.get(PayloadKeys.ASSESSMENT);
        assertEquals(27, assessment.getScore());
        assertEquals("unit-test", assessment.getProcessedBy());
    }

    @Test
    void surfacesForcedRiskFailure() {
        ReleaseCandidate candidate = ReleaseCandidate.newBuilder()
                .setId("release-failure")
                .setForceRiskFailure(true)
                .build();

        assertThrows(
                IllegalStateException.class,
                () -> scorer.score(Map.of(PayloadKeys.CANDIDATE, candidate)));
    }
}
