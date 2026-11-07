package com.themainthread.streamhouse.projection;

import java.util.ArrayList;
import java.util.List;

import com.themainthread.streamhouse.event.InventoryStatusChanged;
import com.themainthread.streamhouse.event.OrderPlaced;
import com.themainthread.streamhouse.event.ShippingStatusChanged;

public record OrderState(long orderId, OrderPlaced checkout, InventoryStatusChanged inventory,
        ShippingStatusChanged shipping, boolean conflict) {

    public static OrderState empty() {
        return new OrderState(0, null, null, null, false);
    }

    public List<String> missingSources() {
        List<String> missing = new ArrayList<>(3);
        if (checkout == null) {
            missing.add("checkout");
        }
        if (inventory == null) {
            missing.add("inventory");
        }
        if (shipping == null) {
            missing.add("shipping");
        }
        return List.copyOf(missing);
    }

    public boolean complete() {
        return !conflict && missingSources().isEmpty();
    }

    public OrderState conflicted() {
        return conflict ? this : new OrderState(orderId, checkout, inventory, shipping, true);
    }
}
