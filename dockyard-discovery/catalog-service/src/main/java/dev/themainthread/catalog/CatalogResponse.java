package dev.themainthread.catalog;

import java.math.BigDecimal;
import java.time.Instant;

public record CatalogResponse(
        String sku,
        BigDecimal price,
        String instanceId,
        String color,
        Instant servedAt) {
}
