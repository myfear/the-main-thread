package com.themainthread.releasegate;

import com.amadeus.flamme.runtime.annotations.FlammeImpl;
import com.google.protobuf.Message;
import com.themainthread.releasegate.proto.ReleaseAssessment;
import com.themainthread.releasegate.proto.ReleaseCandidate;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
@FlammeImpl
@Unremovable
public class RiskScorerImpl implements RiskScorer {

    private static final Logger LOG = Logger.getLogger(RiskScorerImpl.class);

    private final ReleaseGateConfig config;

    RiskScorerImpl(ReleaseGateConfig config) {
        this.config = config;
    }

    @Override
    public Map<String, Message> score(Map<String, Message> payload) {
        ReleaseCandidate candidate = (ReleaseCandidate) payload.get(PayloadKeys.CANDIDATE);
        if (candidate.getForceRiskFailure()) {
            throw new IllegalStateException("forced risk scorer failure");
        }

        delay(candidate.getAnalysisDelayMillis());
        int score = Math.min(
                100,
                candidate.getChangedFiles() * 2 + candidate.getCriticalDependencies() * 15);
        ReleaseAssessment assessment = ReleaseAssessment.newBuilder()
                .setScore(score)
                .setSummary(score < 50 ? "risk stays below the release threshold" : "risk exceeds the release threshold")
                .setProcessedBy(config.nodeId())
                .build();

        Map<String, Message> result = new HashMap<>(payload);
        result.put(PayloadKeys.ASSESSMENT, assessment);
        LOG.infov(
                "node={0} component=risk-scorer release={1} score={2}",
                config.nodeId(),
                candidate.getId(),
                score);
        return result;
    }

    private static void delay(int delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("risk analysis was interrupted", exception);
        }
    }
}
