package com.themainthread.office;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusIntegrationTest
@TestProfile(PackagedTestProfile.class)
class PackagedSmokeIT {
    @Test
    void packagedApplicationServesQuteAndPersistsAConversation() {
        given().get("/").then().statusCode(200).body(containsString("Office Memory"));
        given().get("/api/projects").then().statusCode(200).body("size()", equalTo(2));
        String id = given().contentType("application/json").body(Map.of(
                "projectId", UUID.fromString("00000000-0000-0000-0000-000000000001"), "title", "Packaged smoke test"))
                .post("/api/conversations").then().statusCode(200).extract().path("id");
        given().get("/api/conversations/" + id + "/messages").then().statusCode(200).body("size()", equalTo(0));
    }
}
