package com.acme.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class PriceCalculatorPropertyTest {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final PriceCalculator calculator = new PriceCalculator();

    @Property(tries = 50)
    void totalIsNeverNegative(
            @ForAll("unitPrices") BigDecimal unitPrice,
            @ForAll("quantities") int quantity,
            @ForAll("discounts") int discountPercent) {
        PriceBreakdown result = calculator.calculate(new PriceRequest(unitPrice, quantity, discountPercent));

        assertTrue(result.total().signum() >= 0);
    }

    @Property(tries = 50)
    void largerDiscountNeverIncreasesTotal(
            @ForAll("unitPrices") BigDecimal unitPrice,
            @ForAll("quantities") int quantity,
            @ForAll("discounts") int firstDiscount,
            @ForAll("discounts") int secondDiscount) {
        int lowerDiscount = Math.min(firstDiscount, secondDiscount);
        int higherDiscount = Math.max(firstDiscount, secondDiscount);

        BigDecimal lowerDiscountTotal = calculateTotal(unitPrice, quantity, lowerDiscount);
        BigDecimal higherDiscountTotal = calculateTotal(unitPrice, quantity, higherDiscount);

        assertTrue(higherDiscountTotal.compareTo(lowerDiscountTotal) <= 0);
    }

    @Property(tries = 50)
    void zeroDiscountEqualsUnitPriceTimesQuantity(
            @ForAll("unitPrices") BigDecimal unitPrice, @ForAll("quantities") int quantity) {
        BigDecimal expected = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2);

        assertEquals(expected, calculateTotal(unitPrice, quantity, 0));
    }

    @Property(tries = 50_000)
    void totalMatchesLineLevelRounding(
            @ForAll("unitPrices") BigDecimal unitPrice,
            @ForAll("quantities") int quantity,
            @ForAll("discounts") int discountPercent) {
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal remainingRate = BigDecimal.valueOf(100L - discountPercent).divide(ONE_HUNDRED);
        BigDecimal expected = subtotal.multiply(remainingRate).setScale(2, RoundingMode.HALF_UP);

        assertEquals(
                expected,
                calculateTotal(unitPrice, quantity, discountPercent),
                () -> "unitPrice=" + unitPrice + ", quantity=" + quantity + ", discountPercent="
                        + discountPercent);
    }

    @Provide
    Arbitrary<BigDecimal> unitPrices() {
        return Arbitraries.longs()
                .between(0, 100_000_000)
                .map(cents -> BigDecimal.valueOf(cents, 2));
    }

    @Provide
    Arbitrary<Integer> quantities() {
        return Arbitraries.integers().between(1, 10_000);
    }

    @Provide
    Arbitrary<Integer> discounts() {
        return Arbitraries.integers().between(0, 50);
    }

    private BigDecimal calculateTotal(BigDecimal unitPrice, int quantity, int discountPercent) {
        return calculator.calculate(new PriceRequest(unitPrice, quantity, discountPercent)).total();
    }
}
