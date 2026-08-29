package com.themainthread.pricing;

import java.util.List;

import io.quarkiverse.fory.ForySerialization;

@ForySerialization(classId = 256)
public record PricingSnapshot(
        String snapshotId,
        String customerTier,
        ShippingAddress destination,
        List<LineItem> lines) {
}
