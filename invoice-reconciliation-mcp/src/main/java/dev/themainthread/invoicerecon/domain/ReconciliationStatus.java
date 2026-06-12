package dev.themainthread.invoicerecon.domain;

public enum ReconciliationStatus {
    MATCHED,
    PRICE_VARIANCE,
    QUANTITY_VARIANCE,
    MISSING_PURCHASE_ORDER,
    MISSING_GOODS_RECEIPT,
    CANCELLED
}
