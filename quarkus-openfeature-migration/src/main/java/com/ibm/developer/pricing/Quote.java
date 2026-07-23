package com.ibm.developer.pricing;

import java.math.BigDecimal;

public record Quote(
        String tenantId,
        String pricingEngine,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        String flagOrigin) {
}
