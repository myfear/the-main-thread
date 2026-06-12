package dev.themainthread.invoicerecon.domain;

public enum ReconciliationOutcome {
    COMPLETED,
    CANCELLED,
    ELICITATION_DECLINED,
    ELICITATION_UNSUPPORTED,
    NO_INVOICES_FOUND,
    FAILED
}
