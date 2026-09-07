package com.themainthread.exoplanets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ArchiveFailureTest.UnavailableArchive.class)
class ArchiveFailureTest {
    public static class UnavailableArchive implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.rest-client.nasa.url", "http://127.0.0.1:1",
                    "quarkus.rest-client.nasa.connect-timeout", "1000");
        }
    }

    @Test
    void reportsLiveFailureWithoutPretendingSnapshotDataIsLive() {
        given().queryParam("source", "live").get("/api/dashboard").then().statusCode(503)
                .body("error", containsString("Catalogue unavailable"));
        given().queryParam("source", "snapshot").get("/api/dashboard").then().statusCode(200);
    }
}
