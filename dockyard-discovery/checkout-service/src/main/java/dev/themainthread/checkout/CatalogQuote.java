package dev.themainthread.checkout;

import java.math.BigDecimal;
import java.time.Instant;

public record CatalogQuote(
        String sku,
        BigDecimal price,
        String instanceId,
        String color,
        Instant servedAt) {
}
