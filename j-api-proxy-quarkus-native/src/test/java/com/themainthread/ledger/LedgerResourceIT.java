package com.themainthread.ledger;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class LedgerResourceIT {

    @Test
    void nativeExecutableCanCreateTheRegisteredProxies() {
        given()
                .when().get("/ledger/acct-42")
                .then()
                .statusCode(200)
                .body("accountId", org.hamcrest.Matchers.equalTo("acct-42"));
    }
}
