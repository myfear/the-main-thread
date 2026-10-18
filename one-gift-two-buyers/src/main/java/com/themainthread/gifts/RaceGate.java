package com.themainthread.gifts;

import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.enterprise.context.ApplicationScoped;

/** Coordinates reads for a local demonstration; this does not protect reservations. */
@ApplicationScoped
public class RaceGate {
    private final ConcurrentHashMap<String, CyclicBarrier> gates = new ConcurrentHashMap<>();
    private final DemoConfig config;

    public RaceGate(DemoConfig config) {
        this.config = config;
    }

    public void requireEnabled() {
        if (!config.enabled()) {
            throw new ApiFailure(404, "demo_disabled", "Race demonstrations are disabled.", null);
        }
    }

    public void afterRead(String mode, UUID id) {
        requireEnabled();
        String key = mode + ":" + id;
        CyclicBarrier barrier = gates.computeIfAbsent(key, ignored -> new CyclicBarrier(2));
        try {
            barrier.await(config.waitTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(id);
        } catch (BrokenBarrierException | TimeoutException e) {
            throw unavailable(id);
        } finally {
            gates.remove(key, barrier);
        }
    }

    private ApiFailure unavailable(UUID id) {
        return new ApiFailure(503, "race_incomplete",
                "Both buyers must arrive before the demo timeout. Create a new round and try again.", id);
    }
}
