package com.mainthread.loanflow.loan;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.SecurityAttribute;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
class LoanResourceSecurityTest {

    @Test
    @TestSecurity(user = "alice", roles = { "loan_officer" }, attributes = {
            @SecurityAttribute(key = "branch", value = "berlin")
    })
    void berlinOfficerCanReadBerlinLoan() {
        given()
                .when()
                .get("/api/loans/LN-100")
                .then()
                .statusCode(200)
                .body("id", equalTo("LN-100"))
                .body("branch", equalTo("berlin"))
                .body("status", equalTo("DRAFT"));
    }

    @Test
    @TestSecurity(user = "bob", roles = { "loan_officer" }, attributes = {
            @SecurityAttribute(key = "branch", value = "hamburg")
    })
    void hamburgOfficerCannotReadBerlinLoan() {
        given()
                .when()
                .get("/api/loans/LN-100")
                .then()
                .statusCode(403)
                .body("error", equalTo("access_denied"));
    }

    @Test
    @TestSecurity(user = "admin", roles = { "loan_admin" }, attributes = {
            @SecurityAttribute(key = "branch", value = "hq")
    })
    void loanAdminCanReadAnyBranchLoan() {
        given()
                .when()
                .get("/api/loans/LN-100")
                .then()
                .statusCode(200)
                .body("id", equalTo("LN-100"));
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        given()
                .when()
                .get("/api/loans/LN-100")
                .then()
                .statusCode(401);
    }
}
