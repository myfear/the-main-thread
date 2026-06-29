package io.mainthread.vaultboard.dashboard;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;

import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(JwtTenantTestProfile.class)
class JwtTenantResolutionTest {

    @Test
    void resolvesTenantFromVerifiedJwtClaim() {
        String token = Jwt.upn("alice")
                .issuer("https://auth.vaultboard.example")
                .claim("tenant", "acme")
                .expiresIn(Duration.ofMinutes(15))
                .sign();

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/api/dashboards/tenant")
                .then()
                .statusCode(200)
                .body("tenant", equalTo("acme"));
    }

    @Test
    void missingTenantClaimRejectsRequest() {
        String token = Jwt.upn("alice")
                .issuer("https://auth.vaultboard.example")
                .expiresIn(Duration.ofMinutes(15))
                .sign();

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/api/dashboards/tenant")
                .then()
                .statusCode(401);
    }
}
