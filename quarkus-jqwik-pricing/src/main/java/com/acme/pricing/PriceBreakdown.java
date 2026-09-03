package com.acme.pricing;

import java.math.BigDecimal;

public record PriceBreakdown(
        BigDecimal subtotal, BigDecimal discountAmount, BigDecimal total, String currency) {}
