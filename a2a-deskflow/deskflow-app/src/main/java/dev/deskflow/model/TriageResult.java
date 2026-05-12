package dev.deskflow.model;

public record TriageResult(
        String ticketId,
        String severity,
        String category,
        String remediationHint,
        boolean escalationRequired) {}