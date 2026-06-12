package dev.themainthread.invoicerecon.policy;

import dev.themainthread.invoicerecon.domain.MissingGoodsReceiptAction;

public record ReconciliationPolicy(
        double maximumVariancePercent,
        MissingGoodsReceiptAction missingGoodsReceiptAction,
        boolean postMatchedInvoices,
        String defaultCostCenter) {

    public String idempotencyFragment() {
        return maximumVariancePercent
                + "|"
                + missingGoodsReceiptAction
                + "|"
                + postMatchedInvoices
                + "|"
                + defaultCostCenter;
    }
}
