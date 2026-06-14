package dev.verdictiq.model;

import dev.langchain4j.model.output.structured.Description;

public record ModelVerdict(
        @Description("One of POSITIVE, NEGATIVE, NEUTRAL, or UNCERTAIN")
        Sentiment label,
        @Description("One short sentence explaining the classification")
        String reason) {

    public ModelVerdict normalized() {
        Sentiment safeLabel = label != null ? label : Sentiment.UNCERTAIN;
        String safeReason = reason != null && !reason.isBlank() ? reason : "Model returned no reason.";
        if (safeLabel == label && safeReason.equals(reason)) {
            return this;
        }
        return new ModelVerdict(safeLabel, safeReason);
    }
}
