package com.themainthread.releasegate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = NatsTestResource.class, restrictToAnnotatedClass = true)
class ReleaseResourceTest {

    @Test
    void evaluatesReleaseThroughTheLocalPipeline() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "id": "release-42",
                          "changedFiles": 6,
                          "criticalDependencies": 1,
                          "forceRiskFailure": false,
                          "analysisDelayMillis": 0
                        }
                        """)
                .when()
                .post("/releases/evaluate")
                .then()
                .statusCode(200)
                .body("releaseId", equalTo("release-42"))
                .body("riskScore", equalTo(27))
                .body("approved", equalTo(true))
                .body("processedBy", equalTo("test-node"))
                .body("decidedBy", equalTo("test-node"));
    }

    @Test
    void rejectsARequestWithoutAReleaseId() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "changedFiles": 6,
                          "criticalDependencies": 1,
                          "forceRiskFailure": false,
                          "analysisDelayMillis": 0
                        }
                        """)
                .when()
                .post("/releases/evaluate")
                .then()
                .statusCode(400);
    }
}
