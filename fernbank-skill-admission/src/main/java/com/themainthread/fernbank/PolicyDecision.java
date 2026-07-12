package com.themainthread.fernbank;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PolicyDecision(
        boolean allow,
        String outcome,
        @JsonProperty("policy_version") String policyVersion,
        @JsonProperty("enforcement_mode") String enforcementMode,
        List<PolicyReason> reasons,
        List<PolicyReason> warnings) {

    public static PolicyDecision evaluationFailure(String message) {
        return new PolicyDecision(
                false,
                "deny",
                "unavailable",
                "fail-closed",
                List.of(new PolicyReason("POLICY_EVALUATION_FAILED", message, null)),
                List.of());
    }
}
