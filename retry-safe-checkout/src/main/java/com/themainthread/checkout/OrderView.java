package com.themainthread.checkout;

import java.time.Instant;

public record OrderView(
        long id,
        String sku,
        int quantity,
        OrderStatus status,
        String fulfillmentReference,
        Instant createdAt) {

    static OrderView from(PurchaseOrder order) {
        return new OrderView(
                order.id,
                order.sku,
                order.quantity,
                order.status,
                order.fulfillmentReference,
                order.createdAt);
    }
}
