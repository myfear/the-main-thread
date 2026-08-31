package com.acme.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void keepsValidValues() {
        Product product = new Product(
                "sku-1", "Mechanical Keyboard", new BigDecimal("129.00"), Currency.getInstance("EUR"));

        assertEquals("sku-1", product.sku());
        assertEquals(new BigDecimal("129.00"), product.price());
    }

    @Test
    void rejectsNegativePrices() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new Product(
                        "sku-1", "Mechanical Keyboard", new BigDecimal("-0.01"), Currency.getInstance("EUR")));

        assertEquals("price must not be negative", failure.getMessage());
    }
}
