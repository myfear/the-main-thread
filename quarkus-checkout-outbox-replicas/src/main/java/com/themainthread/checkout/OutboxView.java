package com.themainthread.checkout;

import java.time.Instant;
import java.util.UUID;

public record OutboxView(
        UUID id,
        String aggregatetype,
        String aggregateid,
        String type,
        String payload,
        Instant createdAt,
        Instant publishedAt,
        String claimedBy,
        UUID claimToken,
        Instant claimedUntil,
        int attemptCount) {

    static OutboxView from(OutboxEvent event) {
        return new OutboxView(
                event.id,
                event.aggregatetype,
                event.aggregateid,
                event.type,
                event.payload,
                event.createdAt,
                event.publishedAt,
                event.claimedBy,
                event.claimToken,
                event.claimedUntil,
                event.attemptCount);
    }
}
