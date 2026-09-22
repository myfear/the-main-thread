package com.themainthread.checkout;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jboss.logging.Logger;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OutboxPublisher {

    private static final Logger LOG = Logger.getLogger(OutboxPublisher.class);

    private static final int BATCH_SIZE = 20;

    private final ObjectMapper json;
    private final OrderPlacedProducer producer;
    private final CrashSwitch crashes;

    OutboxPublisher(ObjectMapper json, OrderPlacedProducer producer, CrashSwitch crashes) {
        this.json = json;
        this.producer = producer;
        this.crashes = crashes;
    }

    @Scheduled(every = "{checkout.outbox.poll-every}", identity = "outbox-publisher",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void poll() {
        publishPending();
    }

    public int publishPending() {
        List<UUID> ids = QuarkusTransaction.requiringNew().call(this::unpublishedIds);
        int published = 0;
        for (UUID id : ids) {
            if (publish(id)) {
                published++;
            }
        }
        return published;
    }

    public boolean publishOne(UUID id) {
        return publish(id);
    }

    private List<UUID> unpublishedIds() {
        return OutboxEvent.unpublished(BATCH_SIZE).stream().map(event -> event.id).toList();
    }

    private boolean publish(UUID id) {
        OutboxEvent event = QuarkusTransaction.requiringNew().call(() -> OutboxEvent.findById(id));
        if (event == null || event.publishedAt != null) {
            return false;
        }
        OrderPlaced payload = readPayload(event);
        producer.send(payload);
        crashes.maybeCrash(CrashPoint.KAFKA);
        QuarkusTransaction.requiringNew().run(() -> markPublished(id));
        return true;
    }

    private void markPublished(UUID id) {
        OutboxEvent event = OutboxEvent.findById(id);
        if (event == null || event.publishedAt != null) {
            return;
        }
        event.publishedAt = Instant.now();
        LOG.infof("Published outbox event %s for order %s", id, event.aggregateid);
    }

    private OrderPlaced readPayload(OutboxEvent event) {
        try {
            return json.readValue(event.payload, OrderPlaced.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read outbox payload " + event.id, e);
        }
    }
}
