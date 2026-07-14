package com.themainthread.progress.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.themainthread.progress.TestDataCleaner;
import com.themainthread.progress.domain.ImportProgress;
import com.themainthread.progress.domain.JobState;
import com.themainthread.progress.job.ImportJobScheduler;
import com.themainthread.progress.persistence.JobStore;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@QuarkusTest
class ImportResourceTest {

    @Inject
    TestDataCleaner cleaner;

    @Inject
    ImportJobScheduler scheduler;

    @Inject
    JobStore store;

    @TempDir
    Path directory;

    @BeforeEach
    void setUp() {
        cleaner.clean();
    }

    @Test
    void createsAndCompletesADurableImport() throws IOException {
        Response response = upload(validCsv());
        ImportProgress created = response.then()
                .statusCode(202)
                .body("state", equalTo("QUEUED"))
                .extract().body().as(ImportProgress.class);
        UUID id = created.id();
        assertTrue(response.header("Location").endsWith("/api/imports/" + id));

        scheduler.runNext();

        ImportProgress completed = given()
                .when().get("/api/imports/{id}", id)
                .then().statusCode(200)
                .body("state", equalTo("SUCCEEDED"))
                .body("percent", equalTo(100))
                .body("publishedCount", equalTo(3))
                .extract().as(ImportProgress.class);
        assertEquals(3, store.publishedCount(completed.id()));

        given()
                .accept("text/event-stream")
                .when().get("/api/imports/{id}/events", id)
                .then().statusCode(200)
                .body(containsString("event:progress"))
                .body(containsString("\"state\":\"SUCCEEDED\""));
    }

    @Test
    void cancelsAQueuedImport() throws IOException {
        UUID id = upload(validCsv()).as(ImportProgress.class).id();

        given()
                .when().delete("/api/imports/{id}", id)
                .then().statusCode(202)
                .body("state", equalTo("CANCELLED"))
                .body("message", equalTo("Cancelled before processing started"));

        scheduler.runNext();
        assertEquals(JobState.CANCELLED, store.snapshot(id).state());
        assertEquals(0, store.publishedCount(id));
    }

    @Test
    void invalidCsvFailsWithoutPublishingPartialRows() throws IOException {
        Path csv = write("""
                invoice_number,amount,currency
                INV-1,10.00,EUR
                INV-1,12.00,EUR
                """);
        UUID id = upload(csv).as(ImportProgress.class).id();

        scheduler.runNext();

        given()
                .when().get("/api/imports/{id}", id)
                .then().statusCode(200)
                .body("state", equalTo("FAILED"))
                .body("error", containsString("Duplicate invoice number"))
                .body("publishedCount", equalTo(0));
        assertEquals(0, store.publishedCount(id));
    }

    @Test
    void rejectsFilesThatAreNotCsv() throws IOException {
        Path text = write("not a csv").resolveSibling("invoices.txt");
        Files.move(directory.resolve("invoices.csv"), text);

        given()
                .multiPart("file", text.toFile(), "text/plain")
                .when().post("/api/imports")
                .then().statusCode(400)
                .body("error", equalTo("Only CSV files are accepted"));
    }

    private Response upload(Path csv) {
        return given()
                .multiPart("file", csv.toFile(), "text/csv")
                .when().post("/api/imports");
    }

    private Path validCsv() throws IOException {
        return write("""
                invoice_number,amount,currency
                INV-1,10.00,EUR
                INV-2,20.00,USD
                INV-3,30.00,GBP
                """);
    }

    private Path write(String content) throws IOException {
        return Files.writeString(directory.resolve("invoices.csv"), content);
    }
}
