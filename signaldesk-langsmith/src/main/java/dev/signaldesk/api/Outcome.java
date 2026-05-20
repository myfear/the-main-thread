package dev.signaldesk.api;

/**
 * HTTP-visible result of an assist call (distinct from trace span status).
 */
public enum Outcome {
    OK,
    TOOL_FAILED,
    DEGRADED
}
