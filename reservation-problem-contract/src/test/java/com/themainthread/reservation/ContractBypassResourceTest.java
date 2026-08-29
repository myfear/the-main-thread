package com.themainthread.reservation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class ContractBypassResourceTest {

    @Test
    void webApplicationExceptionWithEntityBypassesProblemContract() {
        given()
                .when()
                .post("/demo/entity-bypass")
                .then()
                .statusCode(400)
                .body("message", equalTo("This request is bad"))
                .body("title", nullValue());
    }

    @Test
    void entityLessWebApplicationExceptionUsesProblemContract() {
        given()
                .when()
                .post("/demo/status-bypass")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("title", equalTo("Bad Request"));
    }
}
