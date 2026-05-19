package com.orderdesk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDto(
        String orderId,
        String customerId,
        List<ProductDto> products,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal shipping,
        BigDecimal total,
        String currency,
        String shippingAddress,
        String billingAddress,
        String status,
        Integer fraudScore,
        Instant createdAt
) {
    public OrderDto {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
    }
}
