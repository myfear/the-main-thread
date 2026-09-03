package com.themainthread.goblin.inventory;

import java.time.temporal.ChronoUnit;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class InventoryGateway {

    private final InventoryClient client;

    public InventoryGateway(@RestClient InventoryClient client) {
        this.client = client;
    }

    @Timeout(value = 750, unit = ChronoUnit.MILLIS)
    @Retry(maxRetries = 1, delay = 50, jitter = 0, delayUnit = ChronoUnit.MILLIS)
    @Fallback(fallbackMethod = "fallbackInventory")
    public InventorySnapshot inventory(String sku) {
        return client.inventory(sku);
    }

    InventorySnapshot fallbackInventory(String sku) {
        return new InventorySnapshot(sku, 0, false, "fallback");
    }
}
