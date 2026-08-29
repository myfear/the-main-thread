package com.themainthread.reservation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class ReservationResourceTest {

    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    @Test
    void successfulReservationReturnsCreated() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"sku":"mouse-1","quantity":1}
                        """)
                .when()
                .post("/reservations")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("sku", equalTo("mouse-1"))
                .body("quantity", equalTo(1));
    }

    @Test
    void unparseableJsonStillUsesProblemMediaType() {
        given()
                .contentType(ContentType.JSON)
                .body("{")
                .when()
                .post("/reservations")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("title", equalTo("Bad Request"))
                .body(not(containsString("JsonParseException")))
                .body(not(containsString("ReservationRequest")));
    }

    @Test
    void unreadableFieldReturnsSanitizedMalformedBody() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"sku":"keyboard-1","quantity":"five"}
                        """)
                .when()
                .post("/reservations")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("title", equalTo("Bad Request"))
                .body("detail", equalTo("Malformed request body"))
                .body("field", equalTo("quantity"))
                .body(not(containsString("Integer")))
                .body(not(containsString("InvalidFormatException")));
    }

    @Test
    void invalidQuantityReturnsValidationProblem() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"sku":"keyboard-1","quantity":0}
                        """)
                .when()
                .post("/reservations")
                .then()
                .statusCode(422)
                .contentType("application/problem+json")
                .body("title", equalTo("Validation failed"))
                .body("violations.field", hasItem("quantity"));
    }

    @Test
    void unknownSkuReturnsNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"sku":"widget-9","quantity":1}
                        """)
                .when()
                .post("/reservations")
                .then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("title", equalTo("Not Found"))
                .body("detail", equalTo("Unknown SKU: widget-9"));
    }

    @Test
    void insufficientStockReturnsStableConflict() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"sku":"keyboard-1","quantity":5}
                        """)
                .when()
                .post("/reservations")
                .then()
                .statusCode(409)
                .contentType("application/problem+json")
                .body("type", equalTo("https://errors.example.com/insufficient-stock"))
                .body("title", equalTo("Insufficient stock"))
                .body("detail", equalTo("The requested quantity is no longer available."))
                .body("instance", equalTo("/reservations"))
                .body("sku", equalTo("keyboard-1"))
                .body("requested", equalTo(5))
                .body("available", equalTo(2));
    }

    @Test
    void unexpectedFailureReturnsSupportIdWithoutInternalMessage() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"sku":"ledger-offline","quantity":1}
                        """)
                .when()
                .post("/reservations")
                .then()
                .statusCode(500)
                .contentType("application/problem+json")
                .body("title", equalTo("Internal Server Error"))
                .body("supportId", matchesPattern(UUID_PATTERN))
                .body("instance", equalTo("/reservations"))
                .body("detail", emptyOrNullString())
                .body(not(containsString("Inventory ledger is unreachable")))
                .body(not(containsString("IllegalStateException")));
    }

    @Test
    void openApiDocumentsProblemJsonForConflict() {
        given()
                .accept(ContentType.JSON)
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .body("paths.'/reservations'.post.responses.'409'.content.'application/problem+json'",
                        notNullValue());
    }
}
