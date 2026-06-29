package io.mainthread.vaultboard.dashboard;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.math.BigDecimal;

import io.mainthread.vaultboard.support.TenantDataCleaner;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DashboardResourceTest {

    @Inject
    TenantDataCleaner tenantDataCleaner;

    @BeforeEach
    void resetBeforeEachTest() {
        tenantDataCleaner.clearAll();
    }

    @AfterEach
    void cleanup() {
        tenantDataCleaner.clearAll();
    }

    @Test
    void acmeDoesNotSeeGlobexData() {
        given()
                .header("X-Tenant", "acme")
                .contentType("application/json")
                .body(new CreateDashboardRequest("ARR", "alice@acme.example", new BigDecimal("120000.00")))
                .when().post("/api/dashboards")
                .then()
                .statusCode(200);

        given()
                .header("X-Tenant", "globex")
                .contentType("application/json")
                .body(new CreateDashboardRequest("Cash Flow", "finops@globex.example", new BigDecimal("98000.00")))
                .when().post("/api/dashboards")
                .then()
                .statusCode(200);

        given()
                .header("X-Tenant", "acme")
                .when().get("/api/dashboards")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].ownerEmail", equalTo("alice@acme.example"));

        given()
                .header("X-Tenant", "globex")
                .when().get("/api/dashboards")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].ownerEmail", equalTo("finops@globex.example"));
    }

    @Test
    void missingHeaderFailsFastInHeaderMode() {
        given()
                .when().get("/api/dashboards")
                .then()
                .statusCode(400);
    }
}
