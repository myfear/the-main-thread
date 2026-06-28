package io.mainthread.licenseledger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class LicenseLedgerResourceTest {

    @Test
    void shouldReturnSampleComponents() {
        given()
                .when().get("/components/demo")
                .then()
                .statusCode(200)
                .body("$", hasSize(3))
                .body("[0].decision", equalTo("approved"))
                .body("[2].decision", equalTo("blocked"));
    }

    @Test
    void shouldReviewSubmittedComponent() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "supplier": "Package URL",
                          "groupId": "com.github.package-url",
                          "artifactId": "packageurl-java",
                          "version": "1.5.0",
                          "licenseExpression": "MIT"
                        }
                        """)
                .when().post("/components/review")
                .then()
                .statusCode(200)
                .body("supplier", equalTo("Package URL"))
                .body("decision", equalTo("approved"))
                .body("purl", equalTo("pkg:maven/com.github.package-url/packageurl-java@1.5.0?type=jar"));
    }
}
