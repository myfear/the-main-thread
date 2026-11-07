package com.themainthread.streamhouse.projection;

import com.themainthread.streamhouse.event.EventValidation;
import com.themainthread.streamhouse.event.OrderEvent;

public final class OrderReducer {
    private OrderReducer() {
    }

    public static OrderState apply(OrderState state, OrderEvent event) {
        EventValidation.required(state, "state");
        EventValidation.required(event, "event");
        if (state.orderId() != 0 && state.orderId() != event.orderId()) {
            throw new IllegalArgumentException("Cannot combine different orders");
        }
        if (event.checkout() != null) {
            if (state.checkout() != null) {
                return state.checkout().equals(event.checkout()) ? state : state.conflicted();
            }
            return new OrderState(event.orderId(), event.checkout(), state.inventory(), state.shipping(), state.conflict());
        }
        if (event.inventory() != null) {
            var incoming = event.inventory();
            var current = state.inventory();
            if (current != null && incoming.revision() < current.revision()) {
                return state;
            }
            if (current != null && incoming.revision() == current.revision()) {
                return incoming.status() == current.status() && incoming.occurredAt().equals(current.occurredAt())
                        ? state : state.conflicted();
            }
            return new OrderState(event.orderId(), state.checkout(), incoming, state.shipping(), state.conflict());
        }
        var incoming = event.shipping();
        var current = state.shipping();
        if (current != null && incoming.revision() < current.revision()) {
            return state;
        }
        if (current != null && incoming.revision() == current.revision()) {
            return incoming.status() == current.status() && incoming.occurredAt().equals(current.occurredAt())
                    ? state : state.conflicted();
        }
        return new OrderState(event.orderId(), state.checkout(), state.inventory(), incoming, state.conflict());
    }
}
