package com.themainthread.shipping;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record QuoteRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 9, fraction = 2) BigDecimal subtotal,
        @NotNull ShippingMethod method) {
}
