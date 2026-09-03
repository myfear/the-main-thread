package com.acme.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class RoundingRegressionTest {

    private final PriceCalculator calculator = new PriceCalculator();

    @Test
    void keepsTheDiscoveredRoundingCase() {
        PriceRequest request = new PriceRequest(new BigDecimal("0.01"), 6, 9);

        PriceBreakdown result = calculator.calculate(request);

        assertEquals(new BigDecimal("0.05"), result.total());
    }
}
