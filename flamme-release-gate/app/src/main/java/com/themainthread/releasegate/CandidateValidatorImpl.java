package com.themainthread.releasegate;

import com.amadeus.flamme.runtime.annotations.FlammeImpl;
import com.google.protobuf.Message;
import com.themainthread.releasegate.proto.ReleaseCandidate;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
@FlammeImpl
@Unremovable
public class CandidateValidatorImpl implements CandidateValidator {

    private static final Logger LOG = Logger.getLogger(CandidateValidatorImpl.class);

    private final ReleaseGateConfig config;

    CandidateValidatorImpl(ReleaseGateConfig config) {
        this.config = config;
    }

    @Override
    public Map<String, Message> validate(Map<String, Message> payload) {
        ReleaseCandidate candidate = (ReleaseCandidate) payload.get(PayloadKeys.CANDIDATE);
        if (candidate == null || candidate.getId().isBlank()) {
            throw new IllegalArgumentException("release id must not be blank");
        }
        LOG.infov(
                "node={0} component=candidate-validator release={1}",
                config.nodeId(),
                candidate.getId());
        return new HashMap<>(payload);
    }
}
