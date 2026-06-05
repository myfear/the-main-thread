package dev.quarkex.nebulatrack.testdata;

import static dev.quarkex.nebulatrack.testdata.support.SatelliteEventModels.anomalyMissingPayload;
import static dev.quarkex.nebulatrack.testdata.support.SatelliteEventModels.anyValidEvent;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import dev.quarkex.nebulatrack.testdata.model.SatelliteEvent;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SatelliteEventResourceTest {

    @Test
    void acceptsValidEvent() {
        SatelliteEvent event = anyValidEvent();

        given()
                .contentType("application/json")
                .body(event)
                .when()
                .post("/events")
                .then()
                .statusCode(202)
                .body("satelliteId", is(event.satelliteId()));
    }

    @Test
    void rejectsAnomalyWithoutPayload() {
        SatelliteEvent event = anomalyMissingPayload();

        given()
                .contentType("application/json")
                .body(event)
                .when()
                .post("/events")
                .then()
                .statusCode(400)
                .body("valid", is(false))
                .body("reason", is("anomaly events require payload"));
    }
}
