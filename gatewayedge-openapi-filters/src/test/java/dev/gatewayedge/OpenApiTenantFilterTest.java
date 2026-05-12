package dev.gatewayedge;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OpenApiTenantFilterTest {

    @Test
    void openapiWithoutTenantHeaderMatchesBasicContract() {
        given().when()
                .get("/q/openapi?format=json")
                .then()
                .statusCode(200)
                .body("paths", not(hasKey("/api/premium/report")));
    }

    @Test
    void openapiForBasicTenantOmitsPremiumPath() {
        given().header("X-Gateway-Tenant", "basic").when()
                .get("/q/openapi?format=json")
                .then()
                .statusCode(200)
                .body("paths", not(hasKey("/api/premium/report")));
    }

    @Test
    void openapiForPremiumTenantIncludesPremiumPath() {
        given().header("X-Gateway-Tenant", "premium").when()
                .get("/q/openapi?format=json")
                .then()
                .statusCode(200)
                .body("paths", hasKey("/api/premium/report"));
    }
}