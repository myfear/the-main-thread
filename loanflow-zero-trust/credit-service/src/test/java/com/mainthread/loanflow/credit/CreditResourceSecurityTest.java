package com.mainthread.loanflow.credit;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(OidcWiremockTestResource.class)
class CreditResourceSecurityTest {

    @Test
    void creditCheckWithoutTokenReturns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"loanId\":\"LN-100\",\"applicantId\":\"alice\"}")
                .when()
                .post("/internal/credit-checks")
                .then()
                .statusCode(401);
    }
}
