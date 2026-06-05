package dev.quarkex.nebulatrack.testdata;

import static dev.quarkex.nebulatrack.testdata.support.SatelliteEventModels.validEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

import dev.quarkex.nebulatrack.testdata.model.SatelliteEvent;
import dev.quarkex.nebulatrack.testdata.model.ValidationResult;
import dev.quarkex.nebulatrack.testdata.service.SatelliteEventService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
@ExtendWith(InstancioExtension.class)
class SatelliteEventBoundaryTest {

    @Inject
    SatelliteEventService service;

    @ParameterizedTest
    @ValueSource(doubles = {-90.1, 90.1})
    void rejectsOutOfRangeLatitude(double latitude) {
        SatelliteEvent event = Instancio.of(validEvent())
                .set(field(SatelliteEvent::latitude), latitude)
                .create();

        ValidationResult result = service.validate(event);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("latitude");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.1, 180.1})
    void rejectsOutOfRangeLongitude(double longitude) {
        SatelliteEvent event = Instancio.of(validEvent())
                .set(field(SatelliteEvent::longitude), longitude)
                .create();

        ValidationResult result = service.validate(event);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("longitude");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAT-001", "SAT-NE-42", "NE-1042", ""})
    void rejectsMalformedSatelliteIds(String satelliteId) {
        SatelliteEvent event = Instancio.of(validEvent())
                .set(field(SatelliteEvent::satelliteId), satelliteId)
                .create();

        ValidationResult result = service.validate(event);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("satelliteId");
    }
}
