package org.helios.analytics;

import java.time.Instant;

public record TelemetryRecord(String deviceId, double lat, double lon, Instant timestamp) {

}