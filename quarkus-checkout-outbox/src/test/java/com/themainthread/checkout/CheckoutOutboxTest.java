package com.themainthread.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.persistence.PersistenceException;
import java.util.concurrent.atomic.AtomicLong;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
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
    OutboxPublisher publisher;

    @Inject
    CrashSwitch crashes;

    @Inject
    FulfillmentConsumer consumer;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @BeforeEach
    void resetCrashSwitch() {
        crashes.reset();
    }

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
    void crashAfterCommitKeepsUnpublishedOutboxThenDelivers() {
        CrashWindowException thrown = assertThrows(
                CrashWindowException.class,
                () -> checkout.place(request("after-commit"), CrashPoint.COMMIT));
        assertEquals(CrashPoint.COMMIT, thrown.point());

        PurchaseOrder order = latestOrder();
        OutboxEvent outbox = Tx.call(() -> OutboxEvent.find("aggregateid", Long.toString(order.id)).firstResult());
        assertNotNull(outbox);
        assertNull(outbox.publishedAt);

        publisher.publishOne(outbox.id);
        awaitFulfillment(order.id);

        OutboxEvent published = Tx.call(() -> OutboxEvent.findById(outbox.id));
        assertNotNull(published.publishedAt);
        assertEquals(1L, Tx.call(() -> Fulfillment.count("orderId", order.id)));
    }

    @Test
    void crashAfterKafkaRepublishesWithoutDuplicateFulfillment() throws Exception {
        PurchaseOrder order = checkout.place(request("after-kafka"), CrashPoint.NONE);
        OutboxEvent outbox = Tx.call(() -> OutboxEvent.find("aggregateid", Long.toString(order.id)).firstResult());

        crashes.arm(CrashPoint.KAFKA);
        assertThrows(CrashWindowException.class, () -> publisher.publishOne(outbox.id));
        awaitFulfillment(order.id);

        OutboxEvent stillUnpublished = Tx.call(() -> OutboxEvent.findById(outbox.id));
        assertNull(stillUnpublished.publishedAt);
        assertEquals(1L, Tx.call(() -> Fulfillment.count("orderId", order.id)));

        publisher.publishOne(outbox.id);
        awaitCommittedDeliveries();
        Awaitility.await().pollInSameThread().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            OutboxEvent marked = Tx.call(() -> OutboxEvent.findById(outbox.id));
            assertNotNull(marked.publishedAt);
            assertEquals(1L, Tx.call(() -> Fulfillment.count("orderId", order.id)));
        });
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

    private void awaitCommittedDeliveries() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            var topic = admin.describeTopics(java.util.List.of("order-placed"))
                    .allTopicNames().get(10, TimeUnit.SECONDS).get("order-placed");
            var latest = topic.partitions().stream().collect(Collectors.toMap(
                    partition -> new TopicPartition("order-placed", partition.partition()),
                    partition -> OffsetSpec.latest()));
            var ends = admin.listOffsets(latest).all().get(10, TimeUnit.SECONDS);
            Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                var committed = admin.listConsumerGroupOffsets("checkout-fulfillment")
                        .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
                for (var entry : ends.entrySet()) {
                    var offset = committed.get(entry.getKey());
                    assertNotNull(offset);
                    assertTrue(offset.offset() >= entry.getValue().offset(),
                            "Consumer has not committed the republished record yet");
                }
            });
        }
    }

    private static CheckoutRequest request(String prefix) {
        return new CheckoutRequest(prefix + "-" + SKUS.incrementAndGet(), 1);
    }

    private static PurchaseOrder latestOrder() {
        return Tx.call(() -> PurchaseOrder.find("order by id desc").firstResult());
    }

    private static void awaitFulfillment(long orderId) {
        Awaitility.await()
                .pollInSameThread()
                .atMost(Duration.ofSeconds(20))
                .until(() -> Tx.call(() -> Fulfillment.findByOrderId(orderId)) != null);
    }
}
