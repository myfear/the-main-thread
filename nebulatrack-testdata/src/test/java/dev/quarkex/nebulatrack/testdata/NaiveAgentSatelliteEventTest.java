package dev.quarkex.nebulatrack.testdata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quarkex.nebulatrack.testdata.model.SatelliteEvent;
import dev.quarkex.nebulatrack.testdata.model.TelemetryState;
import dev.quarkex.nebulatrack.testdata.model.ValidationResult;
import dev.quarkex.nebulatrack.testdata.service.SatelliteEventService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NaiveAgentSatelliteEventTest {

    @Inject
    SatelliteEventService service;

    @Test
    void acceptsNominalEvent() {
        SatelliteEvent event = new SatelliteEvent(
                "EVT-001",
                "SAT-NE-0001",
                0.0,
                0.0,
                400.0,
                TelemetryState.NOMINAL,
                Instant.parse("2024-01-01T00:00:00Z"),
                "{}");

        ValidationResult result = service.validate(event);

        assertThat(result.valid()).isTrue();
    }
}
