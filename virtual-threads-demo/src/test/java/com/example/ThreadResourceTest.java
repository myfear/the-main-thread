package com.example;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ThreadResourceTest {
    @Test
    void dispatchesOnTheExpectedThreads() {
        given().get("/api/demo/event-loop").then().statusCode(200)
                .body("virtual", is(false)).body("name", containsString("eventloop"));
        given().get("/api/demo/worker").then().statusCode(200)
                .body("virtual", is(false)).body("name", containsString("executor"));
        given().get("/api/demo/hello").then().statusCode(200).body("virtual", is(true));
        given().get("/api/demo/query").then().statusCode(200).body("virtual", is(true));
    }

    @Test
    void completesBoundedFanOut() {
        given().get("/api/demo/parallel/10?delayMs=20").then().statusCode(200)
                .body("completed", is(10)).body("allVirtual", is(true));
    }

    @Test
    void rejectsInvalidBoundsAndCancelsExpiredBatches() {
        for (String query : new String[] { "0", "33", "2?delayMs=-1", "2?delayMs=2001",
                "2?timeoutMs=0", "2?timeoutMs=5001" }) {
            given().get("/api/demo/parallel/" + query).then().statusCode(400);
        }
        given().get("/api/demo/parallel/2?delayMs=1000&timeoutMs=10").then().statusCode(504);
        given().get("/api/demo/parallel/2?delayMs=0").then().statusCode(200);
    }
}
