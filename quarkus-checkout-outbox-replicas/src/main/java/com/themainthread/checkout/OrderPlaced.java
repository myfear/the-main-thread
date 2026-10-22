package com.themainthread.checkout;

import java.time.Instant;
import java.util.UUID;

public record OrderPlaced(
        UUID eventId,
        long orderId,
        String sku,
        int quantity,
        Instant placedAt) {

    static OrderPlaced of(PurchaseOrder order, UUID eventId) {
        return new OrderPlaced(eventId, order.id, order.sku, order.quantity, order.createdAt);
    }
}
