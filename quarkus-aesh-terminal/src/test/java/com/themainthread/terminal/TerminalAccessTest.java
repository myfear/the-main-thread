package com.themainthread.terminal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class TerminalAccessTest {

    @Test
    void protectsTheBrowserTerminal() {
        given()
                .redirects().follow(false)
                .when().get("/aesh/index.html")
                .then().statusCode(401);

        given()
                .auth().preemptive().basic("viewer", "terminal")
                .when().get("/aesh/index.html")
                .then().statusCode(403);

        given()
                .auth().preemptive().basic("operator", "terminal")
                .when().get("/aesh/index.html")
                .then().statusCode(200);
    }

    @Test
    void publishesTransportReadiness() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks.name", hasItem("Aesh WebSocket terminal health check"));
    }
}
