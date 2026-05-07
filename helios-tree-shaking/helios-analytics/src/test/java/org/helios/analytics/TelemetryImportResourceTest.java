package org.helios.analytics;

import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@QuarkusTest
class TelemetryImportResourceTest {

    @Test
    void importParsesRows() {
        String csv = ""
                + "deviceId,lat,lon,timestamp\n"
                + "truck-1,53.5511,10.0055,2025-04-02T09:15:30Z\n"
                + "truck-2,48.1351,11.5820,2025-04-02T09:16:01Z\n";

        RestAssured.given()
                .contentType(ContentType.TEXT)
                .body(csv)
                .when()
                .post("/import")
                .then()
                .statusCode(200)
                .body("imported", is(2));
    }
}