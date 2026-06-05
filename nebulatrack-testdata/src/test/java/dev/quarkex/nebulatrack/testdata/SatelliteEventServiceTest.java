package dev.quarkex.nebulatrack.testdata;

import static dev.quarkex.nebulatrack.testdata.support.SatelliteEventModels.anomalyMissingPayload;
import static dev.quarkex.nebulatrack.testdata.support.SatelliteEventModels.anyValidEvent;
import static dev.quarkex.nebulatrack.testdata.support.SatelliteEventModels.withNegativeAltitude;
import static org.assertj.core.api.Assertions.assertThat;

import dev.quarkex.nebulatrack.testdata.model.ValidationResult;
import dev.quarkex.nebulatrack.testdata.service.SatelliteEventService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SatelliteEventServiceTest {

    @Inject
    SatelliteEventService service;

    @RepeatedTest(20)
    void acceptsValidSyntheticEvents() {
        ValidationResult result = service.validate(anyValidEvent());

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsAnomalyWithoutPayload() {
        ValidationResult result = service.validate(anomalyMissingPayload());

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("payload");
    }

    @Test
    void rejectsNegativeAltitude() {
        ValidationResult result = service.validate(withNegativeAltitude());

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("altitude");
    }
}
