package com.acme.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PriceCalculatorExampleTest {

    private final PriceCalculator calculator = new PriceCalculator();

    @ParameterizedTest(name = "{index}: {0} x {1} with {2}% = {3}")
    @CsvSource({
        "0.01, 3, 0, 0.03",
        "0.99, 7, 0, 6.93",
        "12.49, 2, 0, 24.98",
        "199.95, 10, 0, 1999.50",
        "1000000.00, 1, 0, 1000000.00",
        "1.00, 1, 5, 0.95",
        "2.40, 3, 5, 6.84",
        "19.80, 7, 5, 131.67",
        "129.00, 50, 5, 6127.50",
        "0.10, 5, 10, 0.45",
        "2.50, 2, 10, 4.50",
        "19.90, 3, 10, 53.73",
        "249.00, 12, 10, 2689.20",
        "0.20, 2, 15, 0.34",
        "5.00, 7, 15, 29.75",
        "19.80, 5, 15, 84.15",
        "120.00, 25, 15, 2550.00",
        "0.05, 10, 20, 0.40",
        "1.25, 4, 20, 4.00",
        "9.95, 3, 20, 23.88",
        "250.00, 99, 20, 19800.00",
        "0.04, 2, 25, 0.06",
        "1.24, 5, 25, 4.65",
        "49.96, 4, 25, 149.88",
        "1000.00, 10, 25, 7500.00",
        "0.10, 7, 30, 0.49",
        "1.50, 3, 30, 3.15",
        "19.90, 12, 30, 167.16",
        "99.00, 50, 30, 3465.00",
        "0.20, 4, 35, 0.52",
        "2.00, 5, 35, 6.50",
        "24.80, 3, 35, 48.36",
        "200.00, 20, 35, 2600.00",
        "0.05, 5, 40, 0.15",
        "1.25, 2, 40, 1.50",
        "9.95, 7, 40, 41.79",
        "129.90, 10, 40, 779.40",
        "0.20, 3, 45, 0.33",
        "2.00, 4, 45, 4.40",
        "19.80, 12, 45, 130.68",
        "200.00, 25, 45, 2750.00",
        "1.00, 2, 49, 1.02",
        "25.00, 4, 49, 51.00",
        "99.00, 7, 49, 353.43",
        "500.00, 50, 49, 12750.00",
        "0.02, 3, 50, 0.03",
        "1.00, 5, 50, 2.50",
        "19.98, 2, 50, 19.98",
        "129.90, 10, 50, 649.50",
        "1000.00, 99, 50, 49500.00"
    })
    void calculatesExpectedTotal(
            String unitPrice, int quantity, int discountPercent, String expectedTotal) {
        PriceRequest request = new PriceRequest(new BigDecimal(unitPrice), quantity, discountPercent);

        PriceBreakdown result = calculator.calculate(request);

        assertEquals(new BigDecimal(expectedTotal), result.total());
    }
}
