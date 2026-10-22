package com.themainthread.checkout;

import static io.restassured.RestAssured.given;
import java.util.Map;
import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

@QuarkusTest
@TestProfile(DemoDisabledTest.Disabled.class)
class DemoDisabledTest {
    public static class Disabled implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("checkout.demo.enabled", "false");
        }
    }

    @Test
    void demoControlsAreUnavailable() {
        given().get("/demo/status").then().statusCode(404);
        given().post("/demo/publish").then().statusCode(404);
        given().post("/demo/publish/00000000-0000-0000-0000-000000000001").then().statusCode(404);
        given().post("/demo/scheduler/resume").then().statusCode(404);
    }

    @Test
    void crashHeaderIsRejectedBeforePlacingAnOrder() {
        given().contentType(ContentType.JSON).header("X-Crash-After", "claim")
                .body("{\"sku\":\"disabled-crash\",\"quantity\":1}")
                .post("/orders").then().statusCode(400);
    }
}
