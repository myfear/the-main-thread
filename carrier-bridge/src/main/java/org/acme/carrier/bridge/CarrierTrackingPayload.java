package org.acme.carrier.bridge;

import java.time.Instant;

record CarrierTrackingPayload(String trackingId, String carrier, String status, Instant lastUpdated) {
}
