package org.acme.carrier.bridge;

import java.time.Instant;

public record TrackingResponse(String trackingId, String carrier, String status, Instant lastUpdated) {
}
