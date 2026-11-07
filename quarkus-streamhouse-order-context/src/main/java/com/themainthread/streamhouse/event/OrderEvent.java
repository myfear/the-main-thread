package com.themainthread.streamhouse.event;

// An internal envelope: the three public topic contracts remain unchanged.
public record OrderEvent(OrderPlaced checkout, InventoryStatusChanged inventory, ShippingStatusChanged shipping) {
    public OrderEvent {
        int facts = (checkout == null ? 0 : 1) + (inventory == null ? 0 : 1) + (shipping == null ? 0 : 1);
        if (facts != 1) {
            throw new IllegalArgumentException("Exactly one business event is required");
        }
    }

    public static OrderEvent from(String key, OrderPlaced event) {
        EventValidation.required(event, "OrderPlaced");
        EventValidation.orderKey(key, event.orderId());
        return new OrderEvent(event, null, null);
    }

    public static OrderEvent from(String key, InventoryStatusChanged event) {
        EventValidation.required(event, "InventoryStatusChanged");
        EventValidation.orderKey(key, event.orderId());
        return new OrderEvent(null, event, null);
    }

    public static OrderEvent from(String key, ShippingStatusChanged event) {
        EventValidation.required(event, "ShippingStatusChanged");
        EventValidation.orderKey(key, event.orderId());
        return new OrderEvent(null, null, event);
    }

    public long orderId() {
        return checkout != null ? checkout.orderId() : inventory != null ? inventory.orderId() : shipping.orderId();
    }
}
