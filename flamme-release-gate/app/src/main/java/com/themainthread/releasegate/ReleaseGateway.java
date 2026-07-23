package com.themainthread.releasegate;

import com.amadeus.flamme.runtime.annotations.Flamme;
import com.amadeus.flamme.runtime.annotations.Flamme.MultiPayloadKey;
import com.google.protobuf.Message;
import com.themainthread.releasegate.proto.ReleaseAssessment;
import com.themainthread.releasegate.proto.ReleaseCandidate;
import com.themainthread.releasegate.proto.ReleaseDecision;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Flamme(
        serviceName = "release-gateway",
        consumes = {},
        produces = {"candidate-submitted"},
        multiPayloadKeys = {
                @MultiPayloadKey(id = PayloadKeys.CANDIDATE, type = ReleaseCandidate.class),
                @MultiPayloadKey(id = PayloadKeys.ASSESSMENT, type = ReleaseAssessment.class),
                @MultiPayloadKey(id = PayloadKeys.DECISION, type = ReleaseDecision.class)
        })
public interface ReleaseGateway {

    CompletableFuture<Map<String, Message>> evaluate(Map<String, Message> payload);
}
