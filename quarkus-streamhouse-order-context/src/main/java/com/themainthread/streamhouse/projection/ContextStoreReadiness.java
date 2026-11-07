package com.themainthread.streamhouse.projection;

import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;

@Readiness
@ApplicationScoped
public class ContextStoreReadiness implements HealthCheck {
    private final StateStoreReader stores;

    public ContextStoreReadiness(StateStoreReader stores) {
        this.stores = stores;
    }

    @Override
    public HealthCheckResponse call() {
        try {
            stores.read("0");
            return HealthCheckResponse.up("order-context-store");
        } catch (InvalidStateStoreException | IllegalStateException unavailable) {
            return HealthCheckResponse.down("order-context-store");
        }
    }
}
