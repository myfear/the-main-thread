package com.themainthread.pricing;

import io.quarkiverse.fory.ForySerialization;

@ForySerialization(classId = 258)
public record LineItem(
        String sku,
        int quantity,
        long unitPriceCents,
        int weightGrams) {
}
