package com.themainthread.timetraveler;

import java.time.Instant;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class VaultLedgerResourceTest {

    @Test
    void canReadAnAccountSnapshotAtAnEarlierInstant() throws InterruptedException {
        int accountId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "accountNumber": "VL-1000",
                          "openingBalance": 1250.00
                        }
                        """)
                .when()
                .post("/accounts")
                .then()
                .statusCode(201)
                .body("balance", is(1250.0f))
                .extract()
                .path("id");

        Instant beforeChange = Instant.now();
        Thread.sleep(100);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "balance": 950.00,
                          "status": "SUSPENDED"
                        }
                        """)
                .when()
                .put("/accounts/{id}/balance", accountId)
                .then()
                .statusCode(200)
                .body("balance", is(950.0f))
                .body("status", is("SUSPENDED"));

        given()
                .queryParam("asOf", beforeChange.toString())
                .when()
                .get("/accounts/{id}/snapshot", accountId)
                .then()
                .statusCode(200)
                .body("balance", is(1250.0f))
                .body("status", is("ACTIVE"));
    }

    @Test
    void exposesAuditHistoryForAccountHolderChanges() {
        int holderId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "externalId": "CUST-001",
                          "fullName": "Elena Fischer",
                          "email": "elena@vaultledger.dev",
                          "kycStatus": "PENDING"
                        }
                        """)
                .when()
                .post("/holders")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "fullName": "Elena Fischer",
                          "email": "elena.fischer@vaultledger.dev",
                          "kycStatus": "APPROVED"
                        }
                        """)
                .when()
                .put("/holders/{id}", holderId)
                .then()
                .statusCode(200)
                .body("kycStatus", is("APPROVED"));

        given()
                .when()
                .get("/holders/{id}/audit", holderId)
                .then()
                .statusCode(200)
                .body("size()", is(2))
                .body("modificationType", hasItems("ADD", "MOD"));
    }
}
