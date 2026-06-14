package dev.verdictiq.model;

public record DisagreementEvent(
        String verdictId,
        String text,
        Sentiment graniteLabel,
        String graniteReason,
        Sentiment mistralLabel,
        String mistralReason) {
}
