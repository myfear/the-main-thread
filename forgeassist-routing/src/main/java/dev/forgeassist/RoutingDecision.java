package dev.forgeassist;

import java.time.Instant;

/**
 * Immutable record of a single model routing decision.
 * Fired as a CDI event; consumed by observers for logging and metrics.
 */
public record RoutingDecision(
        String prompt,
        Complexity complexity,
        String selectedModel,
        long classificationMillis,
        Instant timestamp) {

    public RoutingDecision(
            String prompt, Complexity complexity, String selectedModel, long classificationMillis) {
        this(prompt, complexity, selectedModel, classificationMillis, Instant.now());
    }
}