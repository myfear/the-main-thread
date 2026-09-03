package com.acme.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PriceCalculator {

    private static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("1000000.00");
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_QUANTITY = 10_000;
    private static final int MAX_DISCOUNT_PERCENT = 50;

    public PriceBreakdown calculate(PriceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body must not be null");
        }
        BigDecimal unitPrice = normalizeUnitPrice(request.unitPrice());
        validateQuantity(request.quantity());
        validateDiscount(request.discountPercent());

        BigDecimal quantity = BigDecimal.valueOf(request.quantity());
        BigDecimal subtotal = unitPrice.multiply(quantity);
        BigDecimal remainingRate = BigDecimal.valueOf(100L - request.discountPercent())
                .divide(ONE_HUNDRED);

        BigDecimal total = subtotal.multiply(remainingRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = subtotal.subtract(total).setScale(2, RoundingMode.HALF_UP);

        return new PriceBreakdown(subtotal, discountAmount, total, "EUR");
    }

    private BigDecimal normalizeUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null) {
            throw new IllegalArgumentException("unitPrice must not be null");
        }
        try {
            unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("unitPrice must have at most two decimal places", exception);
        }
        if (unitPrice.signum() < 0 || unitPrice.compareTo(MAX_UNIT_PRICE) > 0) {
            throw new IllegalArgumentException("unitPrice must be between 0.00 and 1000000.00");
        }
        return unitPrice;
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("quantity must be between 1 and 10000");
        }
    }

    private void validateDiscount(int discountPercent) {
        if (discountPercent < 0 || discountPercent > MAX_DISCOUNT_PERCENT) {
            throw new IllegalArgumentException("discountPercent must be between 0 and 50");
        }
    }
}
