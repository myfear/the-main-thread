package dev.mainthread.refunddesk;

public record RefundResult(
        String refundId,
        DecisionOutcome outcome,
        String reason,
        String reviewer) {
}
