package com.themainthread.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.PersistenceException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class CheckoutOutboxTest {

    private static final AtomicLong SKUS = new AtomicLong();

    @Inject
    CheckoutService checkout;

    @Inject
    FulfillmentConsumer consumer;

    @Test
    void orderAndOutboxCommitTogether() {
        CheckoutRequest request = request("keyboard");
        PurchaseOrder order = checkout.place(request, CrashPoint.NONE);
        OutboxEvent outbox = Tx.call(() -> OutboxEvent.find("aggregateid", Long.toString(order.id)).firstResult());

        assertEquals(request.sku(), order.sku);
        assertEquals(OrderStatus.ACCEPTED, order.status);
        assertEquals(OutboxEvent.AGGREGATE_TYPE, outbox.aggregatetype);
        assertEquals(Long.toString(order.id), outbox.aggregateid);
        assertEquals(OutboxEvent.EVENT_TYPE, outbox.type);
        assertNotNull(outbox.payload);
        assertTrue(outbox.payload.contains("\"orderId\""));
        assertTrue(outbox.payload.contains(Long.toString(order.id)));
    }

    @Test
    void storedPayloadUsesTheUnchangedDeserializer() {
        PurchaseOrder order = checkout.place(request("wire-contract"), CrashPoint.NONE);
        OutboxEvent outbox = Tx.call(() -> OutboxEvent.find("aggregateid", Long.toString(order.id)).firstResult());
        try (var deserializer = new OrderPlacedDeserializer()) {
            OrderPlaced decoded = deserializer.deserialize("order-placed",
                    outbox.payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(outbox.id, decoded.eventId());
            assertEquals(order.id.longValue(), decoded.orderId());
            assertEquals(order.createdAt, decoded.placedAt());
        }
    }

    @Test
    void rollbackLeavesNeitherOrderNorOutbox() {
        CheckoutRequest request = request("rollback-sku");
        long ordersBefore = Tx.call(() -> PurchaseOrder.count());
        long outboxBefore = Tx.call(() -> OutboxEvent.count());

        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            checkout.persistOrderAndOutbox(request);
            throw new IllegalStateException("forced rollback");
        }));

        assertEquals(ordersBefore, Tx.call(() -> PurchaseOrder.count()).longValue());
        assertEquals(outboxBefore, Tx.call(() -> OutboxEvent.count()).longValue());
    }

    @Test
    void crashAfterCommitKeepsOrderAndOutbox() {
        CrashWindowException thrown = assertThrows(CrashWindowException.class,
                () -> checkout.place(request("after-commit"), CrashPoint.COMMIT));
        assertEquals(CrashPoint.COMMIT, thrown.point());
        PurchaseOrder order = Tx.call(() -> PurchaseOrder.find("order by id desc").firstResult());
        OutboxEvent outbox = Tx.call(() -> OutboxEvent.find("aggregateid", Long.toString(order.id)).firstResult());
        assertNotNull(outbox);
        assertTrue(outbox.payload.contains("\"placedAt\""));
        assertEquals(0L, Tx.call(() -> Fulfillment.count("orderId", order.id)));
    }

    @Test
    void consumerIgnoresDuplicateEventId() {
        PurchaseOrder order = checkout.place(request("duplicate-event"), CrashPoint.NONE);
        OutboxEvent outbox = Tx.call(() -> OutboxEvent.find("aggregateid", Long.toString(order.id)).firstResult());
        OrderPlaced event = new OrderPlaced(
                outbox.id,
                order.id,
                order.sku,
                order.quantity,
                order.createdAt);

        consumer.onOrderPlaced(event);
        consumer.onOrderPlaced(event);

        assertEquals(1L, Tx.call(() -> Fulfillment.count("eventId", outbox.id)));
    }

    @Test
    void concurrentDuplicatesCommitOneFulfillment() throws Exception {
        PurchaseOrder order = checkout.place(request("concurrent"), CrashPoint.NONE);
        OrderPlaced event = OrderPlaced.of(order, UUID.randomUUID());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                consumer.onOrderPlaced(event);
                return null;
            });
            var second = workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                consumer.onOrderPlaced(event);
                return null;
            });
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        assertEquals(1L, Tx.call(() -> Fulfillment.count("eventId", event.eventId())));
    }

    @Test
    void foreignKeyViolationIsNotAcknowledgedAsDuplicate() {
        OrderPlaced event = new OrderPlaced(UUID.randomUUID(), Long.MAX_VALUE, "missing-order", 1, Instant.now());
        assertThrows(PersistenceException.class, () -> consumer.onOrderPlaced(event));
        assertEquals(0L, Tx.call(() -> Fulfillment.count("eventId", event.eventId())));
    }

    private static CheckoutRequest request(String prefix) {
        return new CheckoutRequest(prefix + "-" + SKUS.incrementAndGet(), 1);
    }

}
