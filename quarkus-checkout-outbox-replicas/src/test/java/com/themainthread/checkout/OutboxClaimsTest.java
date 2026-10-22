package com.themainthread.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class OutboxClaimsTest {

    private static final Logger LOG = Logger.getLogger(OutboxClaimsTest.class);
    private static final Duration LEASE = Duration.ofSeconds(30);

    @Inject
    OutboxClaims claims;
    @Inject
    CheckoutService checkout;
    @Inject
    OutboxPublisher publisher;
    @Inject
    OrderPlacedProducer producer;
    @Inject
    CrashSwitch crashes;
    @Inject
    CheckoutConfig config;
    @Inject
    ObjectMapper json;
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @BeforeEach
    void drainEarlierFixtures() {
        crashes.reset();
        Tx.run(() -> OutboxEvent.getEntityManager().createNativeQuery("""
                UPDATE outbox_event SET claimed_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE published_at IS NULL AND claim_token IS NOT NULL
                """).executeUpdate());
        while (publisher.publishPending() > 0) {
            // Finish outstanding events created by earlier tests before testing backlog selection.
        }
    }

    @Test
    void simultaneousWorkersAcquireDisjointBatchesAndBothDeliver() throws Exception {
        for (int i = 0; i < 6; i++) {
            event("concurrent-" + i);
        }
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<OutboxClaim> first;
        List<OutboxClaim> second;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> claimAtBarrier("A", ready, start));
            var b = pool.submit(() -> claimAtBarrier("B", ready, start));
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            first = a.get(10, TimeUnit.SECONDS);
            second = b.get(10, TimeUnit.SECONDS);
        }
        assertEquals(3, first.size());
        assertEquals(3, second.size());
        var ids = new HashSet<UUID>();
        for (var batch : List.of(first, second)) {
            for (var claim : batch) {
                assertTrue(ids.add(claim.id()), "Workers received the same live claim");
                producer.send(json.readValue(claim.payload(), OrderPlaced.class));
                assertEquals(1, claims.complete(claim.id(), claim.token()));
            }
        }
        for (UUID id : ids) {
            assertEquals(1, KafkaProbe.records(bootstrap, id).size());
            assertEquals(1L, Tx.call(() -> Fulfillment.count("eventId", id)));
            assertNotNull(row(id).publishedAt);
        }
        LOG.infof("CONCURRENCY workers=2 claims=6 disjoint=true completed=6 fulfillmentRows=6");
    }

    @Test
    void skipLockedTakesOtherRowsBeforeTheLockedTransactionEnds() throws Exception {
        UUID locked = event("locked");
        UUID available = event("available");
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> Tx.run(() -> {
                OutboxEvent.getEntityManager().createNativeQuery("SELECT id FROM outbox_event WHERE id = :id FOR UPDATE")
                        .setParameter("id", locked).getResultList();
                acquired.countDown();
                await(release);
            }));
            try {
                assertTrue(acquired.await(10, TimeUnit.SECONDS));
                var candidate = pool.submit(() -> claims.claim("B", 1, LEASE, null));
                var result = candidate.get(5, TimeUnit.SECONDS);
                assertEquals(List.of(available), result.stream().map(OutboxClaim::id).toList());
                assertFalse(holder.isDone(), "Lock holder must still be inside its transaction");
            } finally {
                release.countDown();
            }
            holder.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void activeLeaseCannotBeStolenAndExpiredLeaseGetsANewToken() {
        UUID id = event("expiry");
        var first = claim("A", id);
        assertTrue(claims.claim("B", 1, LEASE, id).isEmpty());
        expire(id);
        var second = claim("B", id);
        assertNotEquals(first.token(), second.token());
        assertEquals(2, row(id).attemptCount);
        assertEquals("B", row(id).claimedBy);
    }

    @Test
    void staleTokenCannotCompleteAReclaimedRow() {
        UUID id = event("fencing");
        var first = claim("A", id);
        expire(id);
        var second = claim("B", id);
        assertEquals(0, claims.complete(id, first.token()));
        assertNull(row(id).publishedAt);
        assertEquals(second.token(), row(id).claimToken);
        assertEquals(1, claims.complete(id, second.token()));
        assertNotNull(row(id).publishedAt);
        assertNull(row(id).claimToken);
        assertNull(row(id).claimedBy);
        assertNull(row(id).claimedUntil);
        assertEquals(0, claims.complete(id, second.token()));
        LOG.infof("FENCING event=%s T1=%s T2=%s staleUpdates=0 currentUpdates=1", id, first.token(), second.token());
    }

    @Test
    void crashAfterClaimCommitsLeaseWithoutSendingThenAnotherWorkerRecovers() throws Exception {
        UUID id = event("claim-crash");
        crashes.arm(CrashPoint.CLAIM);
        assertEquals(CrashPoint.CLAIM, assertThrows(CrashWindowException.class,
                () -> publisher.publishOne(id)).point());
        var abandoned = row(id);
        assertNotNull(abandoned.claimToken);
        assertNull(abandoned.publishedAt);
        assertEquals(0, KafkaProbe.records(bootstrap, id).size());
        assertFalse(publisher.publishOne(id));
        expire(id);
        var recovery = new OutboxPublisher(json, producer, crashes, claims, config);
        assertNotEquals(publisher.workerId(), recovery.workerId());
        assertTrue(recovery.publishOne(id));
        assertEquals(1, KafkaProbe.records(bootstrap, id).size());
        assertEquals(2, row(id).attemptCount);
    }

    @Test
    void failedSendRemainsRecoverableAndDoesNotRetryBeforeExpiry() throws Exception {
        UUID id = event("failed-send");
        AtomicInteger attempts = new AtomicInteger();
        var failing = new OrderPlacedProducer(null) {
            @Override
            public void send(OrderPlaced event) {
                assertFalse(QuarkusTransaction.isActive());
                attempts.incrementAndGet();
                throw new IllegalStateException("Injected send failure before reaching Kafka");
            }
        };
        var worker = new OutboxPublisher(json, failing, crashes, claims, config);
        assertFalse(worker.publishOne(id));
        assertFalse(worker.publishOne(id));
        assertEquals(1, attempts.get());
        assertNull(row(id).publishedAt);
        expire(id);
        assertTrue(publisher.publishOne(id));
        assertEquals(1, KafkaProbe.records(bootstrap, id).size());
    }

    @Test
    void claimTransactionAndRowLockHaveEndedBeforeKafkaSend() throws Exception {
        UUID id = event("no-open-tx");
        var inspecting = new OrderPlacedProducer(null) {
            @Override
            public void send(OrderPlaced event) {
                assertFalse(QuarkusTransaction.isActive());
                assertNotNull(row(id).claimToken, "Claim must already be committed");
                Tx.run(() -> OutboxEvent.getEntityManager().createNativeQuery(
                        "SELECT id FROM outbox_event WHERE id = :id FOR UPDATE NOWAIT")
                        .setParameter("id", id).getResultList());
                producer.send(event);
            }
        };
        var worker = new OutboxPublisher(json, inspecting, crashes, claims, config);
        assertTrue(worker.publishOne(id));
        assertEquals(1, KafkaProbe.records(bootstrap, id).size());
    }

    @Test
    void producerRejectsAnAmbientTransaction() {
        var order = checkout.place(new CheckoutRequest("ambient", 1), CrashPoint.NONE);
        assertThrows(IllegalStateException.class, () -> Tx.run(
                () -> producer.send(OrderPlaced.of(order, UUID.randomUUID()))));
    }

    @Test
    void kafkaClientRejectsAnOversizedRecordAndTheClaimRemainsRetriable() throws Exception {
        UUID id = event("oversized");
        String original = row(id).payload;
        var payload = json.readValue(original, OrderPlaced.class);
        String oversized = json.writeValueAsString(new OrderPlaced(id, payload.orderId(),
                "x".repeat(1_100_000), payload.quantity(), payload.placedAt()));
        replacePayload(id, oversized);
        assertFalse(publisher.publishOne(id));
        assertNull(row(id).publishedAt);
        assertNotNull(row(id).claimToken);
        assertEquals(0, KafkaProbe.records(bootstrap, id).size());
        replacePayload(id, original);
        expire(id);
        assertTrue(publisher.publishOne(id));
        assertEquals(1, KafkaProbe.records(bootstrap, id).size());
    }

    @Test
    void publisherReportsLostOwnershipAfterAnotherWorkerReclaimsDuringSend() {
        UUID id = event("stale-publisher");
        var stealing = new OrderPlacedProducer(null) {
            @Override
            public void send(OrderPlaced event) {
                expire(id);
                claim("B", id);
            }
        };
        var worker = new OutboxPublisher(json, stealing, crashes, claims, config);
        assertFalse(worker.publishOne(id));
        assertNull(row(id).publishedAt);
        assertEquals("B", row(id).claimedBy);
    }

    private static void replacePayload(UUID id, String payload) {
        Tx.run(() -> OutboxEvent.getEntityManager().createNativeQuery(
                "UPDATE outbox_event SET payload = CAST(:payload AS jsonb) WHERE id = :id")
                .setParameter("payload", payload).setParameter("id", id).executeUpdate());
    }

    private List<OutboxClaim> claimAtBarrier(String worker, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        return claims.claim(worker, 3, LEASE, null);
    }

    private OutboxClaim claim(String worker, UUID id) {
        return claims.claim(worker, 1, LEASE, id).getFirst();
    }

    private UUID event(String sku) {
        var order = checkout.place(new CheckoutRequest(sku, 1), CrashPoint.NONE);
        return Tx.<OutboxEvent>call(() -> OutboxEvent.find("aggregateid", order.id.toString()).firstResult()).id;
    }

    private static OutboxEvent row(UUID id) {
        return Tx.call(() -> OutboxEvent.findById(id));
    }

    private static void expire(UUID id) {
        Tx.run(() -> OutboxEvent.getEntityManager().createNativeQuery(
                "UPDATE outbox_event SET claimed_until = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = :id")
                .setParameter("id", id).executeUpdate());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
