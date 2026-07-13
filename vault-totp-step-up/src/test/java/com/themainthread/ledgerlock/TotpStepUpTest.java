package com.themainthread.ledgerlock;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.vault.VaultTOTPSecretEngine;
import jakarta.inject.Inject;

@QuarkusTest
class TotpStepUpTest {

    @Inject
    VaultTOTPSecretEngine totpEngine;

    @Test
    void aTotpCodeCreatesAShortLivedStepUpTokenAndCannotBeReplayed() {
        String firstFactorToken = given()
                .when().get("/dev/token/alice")
                .then()
                .statusCode(200)
                .extract().path("token");

        given()
                .auth().oauth2(firstFactorToken)
                .when().post("/api/totp/enrollment")
                .then()
                .statusCode(201)
                .header("Cache-Control", startsWith("no-cache"))
                .body("qrCodeDataUrl", startsWith("data:image/png;base64,"))
                .body("manualEntryUri", startsWith("otpauth://totp/"));

        String vaultKey = totpEngine.listKeys().getFirst();
        String code = totpEngine.generateCode(vaultKey);

        String stepUpToken = given()
                .auth().oauth2(firstFactorToken)
                .contentType("application/json")
                .body(Map.of("code", code))
                .when().post("/api/totp/step-up")
                .then()
                .statusCode(200)
                .body("expiresInSeconds", equalTo(120))
                .extract().path("token");

        given()
                .auth().oauth2(firstFactorToken)
                .contentType("application/json")
                .body(new PayoutResource.PayoutRequest("supplier-42", new BigDecimal("1250.00")))
                .when().post("/api/payouts")
                .then()
                .statusCode(403);

        given()
                .auth().oauth2(stepUpToken)
                .contentType("application/json")
                .body(new PayoutResource.PayoutRequest("supplier-42", new BigDecimal("1250.00")))
                .when().post("/api/payouts")
                .then()
                .statusCode(202)
                .body("approvedBy", equalTo("alice"))
                .body("status", equalTo("APPROVED"));

        given()
                .auth().oauth2(firstFactorToken)
                .contentType("application/json")
                .body(Map.of("code", code))
                .when().post("/api/totp/step-up")
                .then()
                .statusCode(401);

        given()
                .auth().oauth2(firstFactorToken)
                .contentType("application/json")
                .body(Map.of("code", code + " "))
                .when().post("/api/totp/step-up")
                .then()
                .statusCode(400);
    }
}
