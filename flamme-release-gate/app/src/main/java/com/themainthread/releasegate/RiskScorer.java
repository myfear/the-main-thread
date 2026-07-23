package com.themainthread.releasegate;

import com.amadeus.flamme.runtime.annotations.Flamme;
import com.amadeus.flamme.runtime.annotations.Flamme.MultiPayloadKey;
import com.google.protobuf.Message;
import com.themainthread.releasegate.proto.ReleaseCandidate;
import java.util.Map;

@Flamme(
        serviceName = "risk-scorer",
        consumes = {"candidate-validated"},
        produces = {"risk-scored"},
        multiPayloadKeys = {
                @MultiPayloadKey(id = PayloadKeys.CANDIDATE, type = ReleaseCandidate.class)
        })
public interface RiskScorer {

    Map<String, Message> score(Map<String, Message> payload);
}
