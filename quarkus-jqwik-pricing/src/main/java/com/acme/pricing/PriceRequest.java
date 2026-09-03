package com.acme.pricing;

import java.math.BigDecimal;

public record PriceRequest(BigDecimal unitPrice, int quantity, int discountPercent) {}
