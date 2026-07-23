package com.themainthread.releasegate;

import com.amadeus.flamme.runtime.annotations.Flamme;
import com.amadeus.flamme.runtime.annotations.Flamme.MultiPayloadKey;
import com.google.protobuf.Message;
import com.themainthread.releasegate.proto.ReleaseAssessment;
import com.themainthread.releasegate.proto.ReleaseCandidate;
import java.util.Map;

@Flamme(
        serviceName = "release-decider",
        consumes = {"risk-scored"},
        produces = {},
        multiPayloadKeys = {
                @MultiPayloadKey(id = PayloadKeys.CANDIDATE, type = ReleaseCandidate.class),
                @MultiPayloadKey(id = PayloadKeys.ASSESSMENT, type = ReleaseAssessment.class)
        })
public interface ReleaseDecider {

    Map<String, Message> decide(Map<String, Message> payload);
}
