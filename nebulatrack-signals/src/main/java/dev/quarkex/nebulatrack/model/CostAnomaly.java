package dev.quarkex.nebulatrack.model;

public record CostAnomaly(String region, double hourlyDelta, Severity severity) {
}
