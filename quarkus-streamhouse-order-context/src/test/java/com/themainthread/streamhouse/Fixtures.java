package com.themainthread.streamhouse;

import java.time.Instant;
import java.util.UUID;

import com.themainthread.streamhouse.event.InventoryStatusChanged;
import com.themainthread.streamhouse.event.OrderPlaced;
import com.themainthread.streamhouse.event.ShippingStatusChanged;

final class Fixtures {
    static final Instant TIME = Instant.parse("2026-10-27T13:10:00Z");
    static final UUID CHECKOUT_ID = UUID.fromString("74c6c857-4dd0-4e05-bdc0-577ba374f7d2");
    static final UUID INVENTORY_ID = UUID.fromString("e77a0a6f-48bb-408d-b7b8-f477b6ab91b7");
    static final UUID SHIPPING_ID = UUID.fromString("0d40b6f3-6a0c-4cdd-91b8-a81ab039609b");

    private Fixtures() {
    }

    static OrderPlaced checkout(long orderId) {
        return new OrderPlaced(CHECKOUT_ID, orderId, "keyboard-1", 1, TIME);
    }

    static InventoryStatusChanged inventory(long orderId, long revision, InventoryStatusChanged.Status status) {
        return new InventoryStatusChanged(INVENTORY_ID, orderId, revision, status, TIME.plusSeconds(60));
    }

    static ShippingStatusChanged shipping(long orderId, long revision, ShippingStatusChanged.Status status) {
        return new ShippingStatusChanged(SHIPPING_ID, orderId, revision, status, TIME.plusSeconds(300));
    }
}
