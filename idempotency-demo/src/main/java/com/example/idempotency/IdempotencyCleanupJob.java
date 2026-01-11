package com.example.idempotency;

import java.time.Instant;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class IdempotencyCleanupJob {

    /**
     * Runs once per hour.
     *
     * Removes idempotency records that are past their expiration time.
     * Uses a bulk delete for efficiency.
     */
    @Scheduled(every = "1h")
    @Transactional
    public void cleanupExpiredKeys() {
        long deleted = IdempotencyRecord.delete(
                "expiresAt IS NOT NULL AND expiresAt < ?1",
                Instant.now());

        if (deleted > 0) {
            // Keep logging lightweight; this is operational signal, not noise
            System.out.println(
                    "Idempotency cleanup removed " + deleted + " expired records");
        }
    }
}
