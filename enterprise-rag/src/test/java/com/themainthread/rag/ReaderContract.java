package com.themainthread.rag;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class ReaderContract {
    @BeforeEach
    void awaitRequiredCorpus() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofMinutes(10).toNanos();
        while (System.nanoTime() < deadline) {
            String status = given().get("/ingestion").then().statusCode(200).extract().path("status");
            if ("READY".equals(status)) {
                return;
            }
            if ("FAILED".equals(status) || "EMPTY".equals(status)) {
                fail("Required corpus is " + status + "; inspect the application log");
            }
            Thread.sleep(250);
        }
        fail("Required corpus did not become ready within ten minutes");
    }

    @Test
    void completeCorpusAndReadinessAgree() {
        given().get("/ingestion").then().statusCode(200).body("documents", is(5)).body("status", is("READY"));
        given().get("/q/health/ready").then().statusCode(200).body("status", is("UP"));
    }

    @Test
    void traceStarterPriceToRetrievedPdf() {
        given().queryParam("q", "What is the monthly Starter-tier price?").get("/retrieval").then().statusCode(200)
                .body("file", hasItem("pricing_packaging_guide_2024.pdf"));
        given().queryParam("q", "What is the monthly Starter-tier price?").get("/bot").then().statusCode(200)
                .body("status", is("answered")).body("response", containsString("499"))
                .body("sources.file", hasItem("pricing_packaging_guide_2024.pdf"))
                .body("sources.text.join(' ')", containsString("499"));
    }

    @Test
    void unsupportedFuturePriceCanBeDeclined() {
        given().queryParam("q", "What will the Starter tier cost in 2028?").get("/bot").then().statusCode(200)
                .body("status", is("not_found"));
    }

    @Test
    void validateOnlyTheStatedInputBoundary() {
        given().queryParam("q", " ").get("/bot").then().statusCode(400).body("status", is("invalid"));
        given().queryParam("q", "x".repeat(1001)).get("/bot").then().statusCode(400);
        given().queryParam("q", "Show CloudX pricing").get("/bot").then().statusCode(200);
    }
}
