package com.themainthread.streamhouse;

import static com.themainthread.streamhouse.event.InventoryStatusChanged.Status.REJECTED;
import static com.themainthread.streamhouse.event.InventoryStatusChanged.Status.RESERVED;
import static com.themainthread.streamhouse.event.ShippingStatusChanged.Status.DELAYED;
import static com.themainthread.streamhouse.event.ShippingStatusChanged.Status.PACKED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.themainthread.streamhouse.event.InventoryStatusChanged;
import com.themainthread.streamhouse.event.OrderEvent;
import com.themainthread.streamhouse.event.OrderPlaced;
import com.themainthread.streamhouse.event.ShippingStatusChanged;
import com.themainthread.streamhouse.projection.OrderReducer;
import com.themainthread.streamhouse.projection.OrderState;

class OrderReducerTest {
    static Stream<OrderEvent> firstEvents() {
        return Stream.of(OrderEvent.from("1042", Fixtures.checkout(1042)),
                OrderEvent.from("1042", Fixtures.inventory(1042, 1, RESERVED)),
                OrderEvent.from("1042", Fixtures.shipping(1042, 1, PACKED)));
    }

    @ParameterizedTest
    @MethodSource("firstEvents")
    void anySourceCanInitializeAndRepeat(OrderEvent event) {
        OrderState state = OrderReducer.apply(OrderState.empty(), event);
        assertEquals(1042, state.orderId());
        assertFalse(state.complete());
        assertEquals(2, state.missingSources().size());
        assertSame(state, OrderReducer.apply(state, event));
    }

    @Test
    void checkoutListsMissingSourcesAndAllSourcesComplete() {
        var state = OrderReducer.apply(OrderState.empty(), OrderEvent.from("1042", Fixtures.checkout(1042)));
        assertEquals(List.of("inventory", "shipping"), state.missingSources());
        state = OrderReducer.apply(state, OrderEvent.from("1042", Fixtures.shipping(1042, 2, DELAYED)));
        state = OrderReducer.apply(state, OrderEvent.from("1042", Fixtures.inventory(1042, 1, RESERVED)));
        assertTrue(state.complete());
        assertTrue(state.missingSources().isEmpty());
    }

    @Test
    void revisionsAreSourceSpecificAndTimestampsCannotReverseThem() {
        var state = OrderReducer.apply(OrderState.empty(), OrderEvent.from("1042", Fixtures.shipping(1042, 2, DELAYED)));
        var older = new ShippingStatusChanged(UUID.randomUUID(), 1042, 1, PACKED, Fixtures.TIME.plusSeconds(9999));
        assertSame(state, OrderReducer.apply(state, OrderEvent.from("1042", older)));
        state = OrderReducer.apply(state, OrderEvent.from("1042", Fixtures.inventory(1042, 1, RESERVED)));
        assertEquals(1, state.inventory().revision());
        state = OrderReducer.apply(state, OrderEvent.from("1042", Fixtures.inventory(1042, 2, REJECTED)));
        assertEquals(REJECTED, state.inventory().status());
        assertSame(state, OrderReducer.apply(state, OrderEvent.from("1042", Fixtures.inventory(1042, 1, RESERVED))));
        var newer = new ShippingStatusChanged(UUID.randomUUID(), 1042, 3, PACKED, Fixtures.TIME.minusSeconds(100));
        assertEquals(3, OrderReducer.apply(state, OrderEvent.from("1042", newer)).shipping().revision());
    }

    @Test
    void equivalentRevisionWithAnotherEventIdIsIdempotent() {
        var state = OrderReducer.apply(OrderState.empty(), OrderEvent.from("1042", Fixtures.shipping(1042, 2, DELAYED)));
        var repeat = new ShippingStatusChanged(UUID.randomUUID(), 1042, 2, DELAYED, state.shipping().occurredAt());
        assertSame(state, OrderReducer.apply(state, OrderEvent.from("1042", repeat)));
        state = OrderReducer.apply(state, OrderEvent.from("1042", Fixtures.inventory(1042, 1, RESERVED)));
        var inventory = new InventoryStatusChanged(UUID.randomUUID(), 1042, 1, RESERVED, state.inventory().occurredAt());
        assertSame(state, OrderReducer.apply(state, OrderEvent.from("1042", inventory)));
    }

    @Test
    void shippingConflictPreservesFactAndStaysVisibleAfterHigherRevision() {
        var state = OrderReducer.apply(OrderState.empty(), OrderEvent.from("1042", Fixtures.shipping(1042, 2, DELAYED)));
        var conflict = OrderReducer.apply(state, OrderEvent.from("1042", Fixtures.shipping(1042, 2, PACKED)));
        assertTrue(conflict.conflict());
        assertEquals(state.shipping(), conflict.shipping());
        conflict = OrderReducer.apply(conflict, OrderEvent.from("1042", Fixtures.checkout(1042)));
        conflict = OrderReducer.apply(conflict, OrderEvent.from("1042", Fixtures.inventory(1042, 1, RESERVED)));
        conflict = OrderReducer.apply(conflict, OrderEvent.from("1042", Fixtures.shipping(1042, 3, PACKED)));
        assertTrue(conflict.missingSources().isEmpty());
        assertFalse(conflict.complete());
        assertTrue(conflict.conflict());
    }

    @Test
    void inventoryConflictPreservesFact() {
        var state = OrderReducer.apply(OrderState.empty(), OrderEvent.from("1042", Fixtures.inventory(1042, 1, RESERVED)));
        var conflict = OrderReducer.apply(state, OrderEvent.from("1042", Fixtures.inventory(1042, 1, REJECTED)));
        assertTrue(conflict.conflict());
        assertEquals(state.inventory(), conflict.inventory());
    }

    @Test
    void equalRevisionWithDifferentOccurrenceTimeIsAConflict() {
        var state = OrderReducer.apply(OrderState.empty(), OrderEvent.from("1042", Fixtures.shipping(1042, 1, PACKED)));
        var changedTime = new ShippingStatusChanged(Fixtures.SHIPPING_ID, 1042, 1, PACKED, Fixtures.TIME);
        assertTrue(OrderReducer.apply(state, OrderEvent.from("1042", changedTime)).conflict());
    }

    @Test
    void checkoutIdentityAndContentAreImmutable() {
        var state = OrderReducer.apply(OrderState.empty(), OrderEvent.from("1042", Fixtures.checkout(1042)));
        var differentId = new OrderPlaced(UUID.randomUUID(), 1042, "keyboard-1", 1, Fixtures.TIME);
        var differentContent = new OrderPlaced(Fixtures.CHECKOUT_ID, 1042, "keyboard-2", 1, Fixtures.TIME);
        for (var event : List.of(differentId, differentContent)) {
            var conflict = OrderReducer.apply(state, OrderEvent.from("1042", event));
            assertTrue(conflict.conflict());
            assertEquals(state.checkout(), conflict.checkout());
        }
    }

    @Test
    void ordersCannotBeMixedAndKeysMustMatch() {
        var state = OrderReducer.apply(OrderState.empty(), OrderEvent.from("1042", Fixtures.checkout(1042)));
        assertThrows(IllegalArgumentException.class,
                () -> OrderReducer.apply(state, OrderEvent.from("1043", Fixtures.checkout(1043))));
        assertThrows(IllegalArgumentException.class, () -> OrderEvent.from("1043", Fixtures.checkout(1042)));
        assertThrows(IllegalArgumentException.class, () -> OrderEvent.from(null, Fixtures.inventory(1042, 1, RESERVED)));
        assertThrows(IllegalArgumentException.class, () -> OrderEvent.from("01042", Fixtures.shipping(1042, 1, PACKED)));
        assertThrows(IllegalArgumentException.class, () -> OrderEvent.from("1042", (OrderPlaced) null));
        assertThrows(IllegalArgumentException.class, () -> new OrderEvent(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new OrderEvent(Fixtures.checkout(1042), Fixtures.inventory(1042, 1, RESERVED), null));
    }
}
