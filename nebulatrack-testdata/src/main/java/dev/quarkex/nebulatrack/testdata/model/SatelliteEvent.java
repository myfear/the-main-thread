package dev.quarkex.nebulatrack.testdata.model;

import java.time.Instant;

public record SatelliteEvent(
        String eventId,
        String satelliteId,
        double latitude,
        double longitude,
        double altitudeKm,
        TelemetryState state,
        Instant observedAt,
        String payloadJson) {
}
