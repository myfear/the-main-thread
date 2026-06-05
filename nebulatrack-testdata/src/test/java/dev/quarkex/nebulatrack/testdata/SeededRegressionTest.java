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
import org.junit.jupiter.api.Test;

@QuarkusTest
class SeededRegressionTest {

    private static final long KNOWN_GOOD_SEED = 948_221_337L;

    @Inject
    SatelliteEventService service;

    @Test
    void seededEventPassesValidation() {
        SatelliteEvent event = Instancio.of(validEvent())
                .withSeed(KNOWN_GOOD_SEED)
                .create();

        ValidationResult result = service.validate(event);

        assertThat(result.valid()).isTrue();
    }
}
