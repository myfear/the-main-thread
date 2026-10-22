package com.themainthread.checkout;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FulfillmentConsumer {

    private static final Logger LOG = Logger.getLogger(FulfillmentConsumer.class);

    private final EntityManager entityManager;

    FulfillmentConsumer(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Incoming("fulfillment")
    @Blocking
    @Transactional
    public void onOrderPlaced(OrderPlaced event) {
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO fulfillment (event_id, order_id, sku, quantity, created_at)
                VALUES (:eventId, :orderId, :sku, :quantity, CURRENT_TIMESTAMP)
                ON CONFLICT (event_id) DO NOTHING
                """)
                .setParameter("eventId", event.eventId())
                .setParameter("orderId", event.orderId())
                .setParameter("sku", event.sku())
                .setParameter("quantity", event.quantity())
                .executeUpdate();
        if (inserted == 0) {
            LOG.infof("Skipping duplicate event %s for order %s", event.eventId(), event.orderId());
        } else {
            LOG.infof("Inserted fulfillment for order %s from event %s", event.orderId(), event.eventId());
        }
    }
}
