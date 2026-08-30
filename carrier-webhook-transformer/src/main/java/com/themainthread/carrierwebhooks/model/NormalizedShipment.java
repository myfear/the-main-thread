package com.themainthread.carrierwebhooks.model;

import java.time.Instant;

public record NormalizedShipment(
        String carrier,
        String eventId,
        String trackingNumber,
        ShipmentStatus status,
        Instant occurredAt) {
}
