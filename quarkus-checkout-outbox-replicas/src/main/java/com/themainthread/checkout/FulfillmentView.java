package com.themainthread.checkout;

import java.time.Instant;
import java.util.UUID;

public record FulfillmentView(
        long id,
        UUID eventId,
        long orderId,
        String sku,
        int quantity,
        Instant createdAt) {

    static FulfillmentView from(Fulfillment fulfillment) {
        return new FulfillmentView(
                fulfillment.id,
                fulfillment.eventId,
                fulfillment.orderId,
                fulfillment.sku,
                fulfillment.quantity,
                fulfillment.createdAt);
    }
}
