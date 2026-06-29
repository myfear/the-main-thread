package dev.mainthread.incidents;

public record IncidentMatch(
        String id,
        float score,
        String service,
        String environment,
        String exceptionType,
        String message,
        String resolvedBy,
        String incidentUrl) {
}
