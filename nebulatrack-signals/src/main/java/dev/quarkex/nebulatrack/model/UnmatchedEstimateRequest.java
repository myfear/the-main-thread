package dev.quarkex.nebulatrack.model;

/**
 * Signal type with no registered receivers — used to prove {@code request()} returns {@code null}.
 */
public record UnmatchedEstimateRequest(String service, int units) {
}
