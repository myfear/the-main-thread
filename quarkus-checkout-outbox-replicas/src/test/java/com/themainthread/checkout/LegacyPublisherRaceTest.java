package com.themainthread.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class LegacyPublisherRaceTest {

    private static final Logger LOG = Logger.getLogger(LegacyPublisherRaceTest.class);

    @Inject
    CheckoutService checkout;
    @Inject
    OrderPlacedProducer producer;
    @Inject
    ObjectMapper json;
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Test
    void twoUnclaimedReadersPublishTheSameUuid() throws Exception {
        var order = checkout.place(new CheckoutRequest("legacy-race", 1), CrashPoint.NONE);
        OutboxEvent event = Tx.call(() -> OutboxEvent.find("aggregateid", order.id.toString()).firstResult());
        CountDownLatch read = new CountDownLatch(2);
        CountDownLatch send = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> publishWithoutClaim(event.id, read, send));
            var b = pool.submit(() -> publishWithoutClaim(event.id, read, send));
            try {
                assertTrue(read.await(10, TimeUnit.SECONDS));
            } finally {
                send.countDown();
            }
            assertTrue(a.get(20, TimeUnit.SECONDS));
            assertTrue(b.get(20, TimeUnit.SECONDS));
        }
        var records = KafkaProbe.records(bootstrap, event.id);
        assertEquals(2, records.size());
        assertEquals(1L, Tx.call(() -> Fulfillment.count("eventId", event.id)));
        assertNotNull(Tx.call(() -> OutboxEvent.<OutboxEvent>findById(event.id)).publishedAt);
        LOG.infof("BASELINE event=%s kafkaRecords=%d fulfillmentRows=1 offsets=%s", event.id, records.size(),
                records.stream().map(r -> r.partition() + ":" + r.offset()).toList());
    }

    // Test-only copy of the original read -> send -> mark algorithm, with a barrier after the read.
    private boolean publishWithoutClaim(UUID id, CountDownLatch read, CountDownLatch send) throws Exception {
        OutboxEvent event = Tx.call(() -> OutboxEvent.findById(id));
        if (event == null || event.publishedAt != null) {
            return false;
        }
        read.countDown();
        assertTrue(send.await(10, TimeUnit.SECONDS));
        producer.send(json.readValue(event.payload, OrderPlaced.class));
        Tx.run(() -> {
            OutboxEvent current = OutboxEvent.findById(id);
            if (current.publishedAt == null) {
                current.publishedAt = Instant.now();
            }
        });
        return true;
    }
}
