package com.themainthread.shipping;

import java.math.BigDecimal;

public record ShippingQuote(BigDecimal subtotal, ShippingMethod method, BigDecimal shipping, String currency) {
}
