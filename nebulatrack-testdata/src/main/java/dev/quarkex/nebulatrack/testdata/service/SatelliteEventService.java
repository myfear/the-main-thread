package dev.quarkex.nebulatrack.testdata.service;

import dev.quarkex.nebulatrack.testdata.model.SatelliteEvent;
import dev.quarkex.nebulatrack.testdata.model.TelemetryState;
import dev.quarkex.nebulatrack.testdata.model.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.regex.Pattern;

@ApplicationScoped
public class SatelliteEventService {

    static final Pattern SATELLITE_ID = Pattern.compile("SAT-[A-Z]{2}-\\d{4}");

    public ValidationResult validate(SatelliteEvent event) {
        if (event == null) {
            return ValidationResult.reject("event is required");
        }
        if (event.eventId() == null || event.eventId().isBlank()) {
            return ValidationResult.reject("eventId is required");
        }
        if (event.satelliteId() == null || !SATELLITE_ID.matcher(event.satelliteId()).matches()) {
            return ValidationResult.reject("satelliteId must match SAT-XX-0000");
        }
        if (event.latitude() < -90.0 || event.latitude() > 90.0) {
            return ValidationResult.reject("latitude out of range");
        }
        if (event.longitude() < -180.0 || event.longitude() > 180.0) {
            return ValidationResult.reject("longitude out of range");
        }
        if (event.altitudeKm() < 0.0) {
            return ValidationResult.reject("altitude cannot be negative");
        }
        if (event.state() == TelemetryState.ANOMALY
                && (event.payloadJson() == null || event.payloadJson().isBlank())) {
            return ValidationResult.reject("anomaly events require payload");
        }
        return ValidationResult.ok();
    }
}
