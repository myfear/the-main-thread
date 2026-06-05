package dev.quarkex.nebulatrack.testdata.support;

import static org.instancio.Select.field;

import dev.quarkex.nebulatrack.testdata.model.SatelliteEvent;
import dev.quarkex.nebulatrack.testdata.model.TelemetryState;
import net.datafaker.Faker;
import org.instancio.Instancio;
import org.instancio.Model;

public final class SatelliteEventModels {

    private static final Faker FAKER = new Faker();

    private SatelliteEventModels() {
    }

    public static Model<SatelliteEvent> validEvent() {
        return Instancio.of(SatelliteEvent.class)
                .generate(field(SatelliteEvent::eventId),
                        gen -> gen.string().prefix("EVT-").length(12))
                .supply(field(SatelliteEvent::satelliteId),
                        () -> FAKER.regexify("SAT-[A-Z]{2}-[0-9]{4}"))
                .generate(field(SatelliteEvent::latitude),
                        gen -> gen.doubles().range(-90.0, 90.0))
                .generate(field(SatelliteEvent::longitude),
                        gen -> gen.doubles().range(-180.0, 180.0))
                .generate(field(SatelliteEvent::altitudeKm),
                        gen -> gen.doubles().range(200.0, 35_786.0))
                .generate(field(SatelliteEvent::state),
                        gen -> gen.oneOf(TelemetryState.NOMINAL, TelemetryState.DEGRADED))
                .generate(field(SatelliteEvent::observedAt),
                        gen -> gen.temporal().instant().past())
                .generate(field(SatelliteEvent::payloadJson),
                        gen -> gen.oneOf("{}", "{\"signal\":\"ok\"}"))
                .toModel();
    }

    public static SatelliteEvent anyValidEvent() {
        return Instancio.create(validEvent());
    }

    public static SatelliteEvent anomalyMissingPayload() {
        return Instancio.of(validEvent())
                .set(field(SatelliteEvent::state), TelemetryState.ANOMALY)
                .set(field(SatelliteEvent::payloadJson), " ")
                .create();
    }

    public static SatelliteEvent withNegativeAltitude() {
        return Instancio.of(validEvent())
                .set(field(SatelliteEvent::altitudeKm), -1.0)
                .create();
    }

    public static String randomSatelliteId() {
        return FAKER.regexify("SAT-[A-Z]{2}-[0-9]{4}");
    }
}
