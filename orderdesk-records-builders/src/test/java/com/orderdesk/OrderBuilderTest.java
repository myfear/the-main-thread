package com.orderdesk;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class OrderBuilderTest {

    @Test
    void shouldFailWithoutCustomerId() {
        ProductDto product = new ProductDto(1L, "Keyboard", new BigDecimal("100"));

        assertThrows(IllegalStateException.class, () -> new OrderDtoBuilder()
                .orderId("ORD-1")
                .addProduct(product)
                .build());
    }

    @Test
    void shouldFailWithoutProducts() {
        assertThrows(IllegalStateException.class, () -> new OrderDtoBuilder()
                .orderId("ORD-1")
                .customerId("customer-1")
                .build());
    }

    @Test
    void recordRejectsBlankOrderId() {
        assertThrows(IllegalArgumentException.class, () -> new OrderDto(
                "",
                "customer-1",
                java.util.List.of(new ProductDto(1L, "Keyboard", new BigDecimal("10"))),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "EUR",
                "addr",
                "addr",
                "CREATED",
                0,
                java.time.Instant.now()));
    }
}
