package com.mainthread.loanflow.document;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(OidcWiremockTestResource.class)
class DocumentResourceSecurityTest {

    @Test
    void documentWriteWithoutTokenReturns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"loanId\":\"LN-100\",\"submittedBy\":\"alice\",\"branch\":\"berlin\",\"creditBand\":\"A\"}")
                .when()
                .post("/internal/documents")
                .then()
                .statusCode(401);
    }
}
