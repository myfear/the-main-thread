package com.themainthread.pricing;

import io.quarkiverse.fory.ForySerialization;

@ForySerialization(classId = 259)
public record QuoteDecision(
        String snapshotId,
        long subtotalCents,
        long shippingCents,
        long totalCents,
        int deliveryDays) {
}
