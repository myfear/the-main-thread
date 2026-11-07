package com.themainthread.streamhouse.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.themainthread.streamhouse.event.InventoryStatusChanged;
import com.themainthread.streamhouse.event.ShippingStatusChanged;
import com.themainthread.streamhouse.projection.LivenessEvaluation.Signal;
import com.themainthread.streamhouse.projection.OrderState;

public record OrderContextResponse(long orderId, Checkout checkout, Inventory inventory, Shipping shipping,
        boolean complete, List<String> missingSources, boolean conflict, Map<String, Signal> sourceSignals,
        String processorState, boolean catchUpVerified) {

    public enum CheckoutStatus {
        PLACED
    }

    public record Checkout(CheckoutStatus status, String sku, int quantity, Instant placedAt) {
    }

    public record Inventory(InventoryStatusChanged.Status status, long revision, Instant occurredAt) {
    }

    public record Shipping(ShippingStatusChanged.Status status, long revision, Instant occurredAt) {
    }

    public static OrderContextResponse from(OrderState state, Map<String, Signal> signals, String processorState) {
        var checkout = state.checkout();
        var inventory = state.inventory();
        var shipping = state.shipping();
        return new OrderContextResponse(state.orderId(),
                checkout == null ? null : new Checkout(CheckoutStatus.PLACED, checkout.sku(), checkout.quantity(), checkout.placedAt()),
                inventory == null ? null : new Inventory(inventory.status(), inventory.revision(), inventory.occurredAt()),
                shipping == null ? null : new Shipping(shipping.status(), shipping.revision(), shipping.occurredAt()),
                state.complete(), state.missingSources(), state.conflict(), Map.copyOf(signals), processorState, false);
    }
}
