package com.themainthread.streamhouse.event;

import java.time.Instant;
import java.util.UUID;

public record OrderPlaced(UUID eventId, long orderId, String sku, int quantity, Instant placedAt) {
    public OrderPlaced {
        EventValidation.required(eventId, "eventId");
        EventValidation.positive(orderId, "orderId");
        if (EventValidation.required(sku, "sku").isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        EventValidation.positive(quantity, "quantity");
        EventValidation.required(placedAt, "placedAt");
    }
}
