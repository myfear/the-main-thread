package com.themainthread.policy;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AuthorizationResourceTest {

    @Test
    void allowsExplicitAllowDecision() {
        given()
                .when().get("/authorization/ALLOW")
                .then()
                .statusCode(200)
                .body("decision", is("ALLOW"))
                .body("allowed", is(true));
    }

    @Test
    void deniesExplicitDenyDecision() {
        given()
                .when().get("/authorization/DENY")
                .then()
                .statusCode(403)
                .body("decision", is("DENY"))
                .body("allowed", is(false));
    }

    @Test
    void failsClosedForUnknownDecision() {
        given()
                .when().get("/authorization/REVIEW")
                .then()
                .statusCode(403)
                .body("decision", is("REVIEW"))
                .body("allowed", is(false));
    }
}
