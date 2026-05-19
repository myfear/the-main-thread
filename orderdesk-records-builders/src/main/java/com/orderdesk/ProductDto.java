package com.orderdesk;

import java.math.BigDecimal;

public record ProductDto(
        Long id,
        String name,
        BigDecimal price
) {
}
