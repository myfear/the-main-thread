package dev.verdictiq.model;

public record PanelVerdict(
        String id,
        String text,
        VerdictStatus status,
        Sentiment graniteLabel,
        String graniteReason,
        Sentiment mistralLabel,
        String mistralReason,
        boolean agreement,
        Sentiment finalVerdict,
        String finalReason,
        boolean abstained) {

    public static PanelVerdict pending(String id, String text) {
        return new PanelVerdict(id, text, VerdictStatus.PENDING, null, null, null, null, false, null, null, false);
    }

    public static PanelVerdict consensus(String id, String text, ModelVerdict granite, ModelVerdict mistral) {
        return new PanelVerdict(
                id,
                text,
                VerdictStatus.COMPLETE,
                granite.label(),
                granite.reason(),
                mistral.label(),
                mistral.reason(),
                true,
                granite.label(),
                "Panel consensus",
                false);
    }

    public static PanelVerdict disagreement(String id, String text, ModelVerdict granite, ModelVerdict mistral) {
        return new PanelVerdict(
                id,
                text,
                VerdictStatus.PENDING,
                granite.label(),
                granite.reason(),
                mistral.label(),
                mistral.reason(),
                false,
                null,
                null,
                false);
    }

    public PanelVerdict adjudicated(Sentiment finalVerdict, String finalReason, boolean abstained) {
        return new PanelVerdict(
                id,
                text,
                VerdictStatus.COMPLETE,
                graniteLabel,
                graniteReason,
                mistralLabel,
                mistralReason,
                agreement,
                finalVerdict,
                finalReason,
                abstained);
    }

    public PanelVerdict failed(String failureReason) {
        return new PanelVerdict(
                id,
                text,
                VerdictStatus.FAILED,
                graniteLabel,
                graniteReason,
                mistralLabel,
                mistralReason,
                agreement,
                null,
                failureReason,
                false);
    }
}
