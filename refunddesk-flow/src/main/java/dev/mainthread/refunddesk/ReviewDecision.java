package dev.mainthread.refunddesk;

public record ReviewDecision(
        String refundId,
        DecisionOutcome outcome,
        String reviewer,
        String note) {
}
