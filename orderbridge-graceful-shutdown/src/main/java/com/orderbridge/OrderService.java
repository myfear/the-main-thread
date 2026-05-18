package com.orderbridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    private final Map<String, String> statuses = new ConcurrentHashMap<>();

    @ConfigProperty(name = "orderbridge.handoff.delay-ms", defaultValue = "5000")
    long handoffDelayMs;

    public OrderStatus status(String orderId) {
        String status = statuses.getOrDefault(orderId, "CREATED");
        return new OrderStatus(orderId, status);
    }

    public HandoffResult handoff(HandoffRequest request) {
        long started = System.currentTimeMillis();
        LOG.infof("Starting payment handoff for order %s", request.orderId());
        statuses.put(request.orderId(), "HANDOFF_IN_PROGRESS");

        try {
            Thread.sleep(handoffDelayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            statuses.put(request.orderId(), "HANDOFF_INTERRUPTED");
            throw new IllegalStateException("Payment handoff interrupted during shutdown", interrupted);
        }

        statuses.put(request.orderId(), "HANDOFF_COMPLETE");
        long elapsedMs = System.currentTimeMillis() - started;
        LOG.infof("Payment handoff complete for order %s in %d ms", request.orderId(), elapsedMs);
        return new HandoffResult(request.orderId(), "HANDOFF_COMPLETE", elapsedMs);
    }
}
