package io.mainthread.vaultboard.dashboard;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(HostTenantTestProfile.class)
class HostTenantResolutionTest {

    @Test
    void resolvesTenantFromHostHeader() {
        given()
                .header("Host", "acme.vaultboard.example")
                .when().get("/api/dashboards/tenant")
                .then()
                .statusCode(200)
                .body("tenant", equalTo("acme"));
    }

    @Test
    void rejectsUnknownTenantHost() {
        given()
                .header("Host", "unknown.vaultboard.example")
                .when().get("/api/dashboards/tenant")
                .then()
                .statusCode(401);
    }
}
