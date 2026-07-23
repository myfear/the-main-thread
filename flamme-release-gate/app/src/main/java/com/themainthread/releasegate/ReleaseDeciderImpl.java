package com.themainthread.releasegate;

import com.amadeus.flamme.runtime.annotations.FlammeImpl;
import com.google.protobuf.Message;
import com.themainthread.releasegate.proto.ReleaseAssessment;
import com.themainthread.releasegate.proto.ReleaseCandidate;
import com.themainthread.releasegate.proto.ReleaseDecision;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
@FlammeImpl
@Unremovable
public class ReleaseDeciderImpl implements ReleaseDecider {

    private static final int APPROVAL_THRESHOLD = 50;
    private static final Logger LOG = Logger.getLogger(ReleaseDeciderImpl.class);

    private final ReleaseGateConfig config;

    ReleaseDeciderImpl(ReleaseGateConfig config) {
        this.config = config;
    }

    @Override
    public Map<String, Message> decide(Map<String, Message> payload) {
        ReleaseCandidate candidate = (ReleaseCandidate) payload.get(PayloadKeys.CANDIDATE);
        ReleaseAssessment assessment = (ReleaseAssessment) payload.get(PayloadKeys.ASSESSMENT);
        boolean approved = assessment.getScore() < APPROVAL_THRESHOLD;
        ReleaseDecision decision = ReleaseDecision.newBuilder()
                .setApproved(approved)
                .setReason(approved ? "approved for release" : "manual review required")
                .setDecidedBy(config.nodeId())
                .build();

        Map<String, Message> result = new HashMap<>(payload);
        result.put(PayloadKeys.DECISION, decision);
        LOG.infov(
                "node={0} component=release-decider release={1} approved={2}",
                config.nodeId(),
                candidate.getId(),
                approved);
        return result;
    }
}
