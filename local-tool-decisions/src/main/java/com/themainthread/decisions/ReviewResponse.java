package com.themainthread.decisions;

import java.util.Map;

public record ReviewResponse(Outcome outcome, String reason, String toolName, String sideEffect,
        String modelName, long latencyMillis, Scores scores) {
    public enum Outcome {
        ALLOW, REVIEW_REQUIRED, DENY
    }

    public record Scores(String intent, Map<String, Double> intentProbabilities,
            double intentMargin, double alignment) {
    }
}
