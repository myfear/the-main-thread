package com.themainthread.policy;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(UnpatchedShimProfile.class)
class UnpatchedBehaviorTest {

    @Test
    void provesTheVendorBehaviorFailsOpenWithoutTheShim() {
        given()
                .when().get("/authorization/REVIEW")
                .then()
                .statusCode(200)
                .body("decision", is("REVIEW"))
                .body("allowed", is(true));
    }
}
