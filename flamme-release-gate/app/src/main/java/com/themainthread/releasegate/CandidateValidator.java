package com.themainthread.releasegate;

import com.amadeus.flamme.runtime.annotations.Flamme;
import com.amadeus.flamme.runtime.annotations.Flamme.MultiPayloadKey;
import com.google.protobuf.Message;
import com.themainthread.releasegate.proto.ReleaseCandidate;
import java.util.Map;

@Flamme(
        serviceName = "candidate-validator",
        consumes = {"candidate-submitted"},
        produces = {"candidate-validated"},
        multiPayloadKeys = {
                @MultiPayloadKey(id = PayloadKeys.CANDIDATE, type = ReleaseCandidate.class)
        })
public interface CandidateValidator {

    Map<String, Message> validate(Map<String, Message> payload);
}
