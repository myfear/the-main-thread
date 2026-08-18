package com.ibm.developer.shieldstral.policy;

public record SafetyAssessment(
        String policy,
        PolicyDirection direction,
        SafetyStatus status,
        Double unsafeScore,
        double threshold,
        boolean blocked,
        String reason) {
}
