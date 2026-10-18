package com.themainthread.gluhwein;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.WebApplicationException;

import org.junit.jupiter.api.Test;

class OrderStoreTest {
    @Test
    void reconnectGetsCurrentStateAndCancellationRemovesListeners() {
        OrderStore store = new OrderStore();
        Order order = store.create("Mia", Order.Drink.RED, 2);
        var received = new ArrayList<Order>();
        var subscription = store.watch(order.id()).subscribe().with(received::add);
        assertEquals(Order.Status.QUEUED, received.getFirst().status());
        assertEquals(1, store.subscriberCount());
        subscription.cancel();
        assertEquals(0, store.subscriberCount());
        store.advance(order.id(), 1);
        store.advance(order.id(), 2);
        var reconnect = store.watch(order.id()).subscribe().with(received::add);
        assertEquals(2, received.size());
        assertEquals(Order.Status.READY, received.getLast().status());
        assertEquals(3, received.getLast().revision());
        reconnect.cancel();
        assertEquals(0, store.subscriberCount());
    }

    @Test
    void competingCountersAdvanceOnlyOnce() throws Exception {
        OrderStore store = new OrderStore();
        Order order = store.create("Noah", Order.Drink.APPLE, 1);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> advanceAfter(start, store, order));
            var second = executor.submit(() -> advanceAfter(start, store, order));
            start.countDown();
            assertEquals(1, first.get(5, TimeUnit.SECONDS) + second.get(5, TimeUnit.SECONDS));
        }
        assertEquals(Order.Status.PREPARING, store.get(order.id()).status());
        assertEquals(2, store.get(order.id()).revision());
    }

    private int advanceAfter(CountDownLatch start, OrderStore store, Order order) throws InterruptedException {
        start.await();
        try {
            store.advance(order.id(), 1);
            return 1;
        } catch (WebApplicationException conflict) {
            assertEquals(409, conflict.getResponse().getStatus());
            return 0;
        }
    }

    @Test
    void subscribingWhileAnUpdateRunsCannotLoseTheCurrentState() throws Exception {
        for (int run = 0; run < 50; run++) {
            OrderStore store = new OrderStore();
            Order order = store.create("Ada", Order.Drink.WHITE, 1);
            var ready = new CompletableFuture<Order>();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var update = executor.submit(() -> store.advance(order.id(), 1));
                var subscription = store.watch(order.id()).subscribe().with(snapshot -> {
                    if (snapshot.revision() == 2) {
                        ready.complete(snapshot);
                    }
                });
                assertEquals(Order.Status.PREPARING, ready.get(5, TimeUnit.SECONDS).status());
                update.get(5, TimeUnit.SECONDS);
                subscription.cancel();
                assertEquals(0, store.subscriberCount());
            }
        }
    }

    @Test
    void terminalOrdersAndCapacityAreBounded() {
        OrderStore store = new OrderStore();
        Order order = store.create("Mia", Order.Drink.RED, 1);
        store.advance(order.id(), 1);
        store.advance(order.id(), 2);
        assertEquals(409, assertThrows(WebApplicationException.class,
                () -> store.advance(order.id(), 3)).getResponse().getStatus());
        for (int i = 1; i < 100; i++) {
            store.create("Guest", Order.Drink.RED, 1);
        }
        assertEquals(503, assertThrows(WebApplicationException.class,
                () -> store.create("One more", Order.Drink.RED, 1)).getResponse().getStatus());
    }
}
