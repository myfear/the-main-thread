package dev.morling.mainthread.vaultflow;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestHTTPEndpoint(DocumentResource.class)
class DocumentResourceTest {

    @Test
    void shouldReturnSeededDocumentsForOwner() {
        given()
                .queryParam("ownerEmail", "legal@parchment.example")
                .when()
                .get("/search")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("[0].ownerEmail", equalTo("legal@parchment.example"));
    }

    @Test
    void shouldCreateAndReadDocument() {
        String payload = """
                {
                  "externalId": "DOC-3000",
                  "ownerEmail": "ops@parchment.example",
                  "title": "Late customs memo for route 3000",
                  "storageKey": "docs/2026/06/DOC-3000.pdf",
                  "checksum": "sha256:33bbca6ab4b22000"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post()
                .then()
                .statusCode(201)
                .header("Location", endsWith("/documents/DOC-3000"))
                .body("externalId", equalTo("DOC-3000"))
                .body("ownerEmail", equalTo("ops@parchment.example"));

        given()
                .when()
                .get("/DOC-3000")
                .then()
                .statusCode(200)
                .body("title", equalTo("Late customs memo for route 3000"))
                .body("storageKey", equalTo("docs/2026/06/DOC-3000.pdf"));
    }

    @Test
    void shouldRejectDuplicateExternalId() {
        String payload = """
                {
                  "externalId": "DOC-1000",
                  "ownerEmail": "legal@parchment.example",
                  "title": "Duplicate seed document",
                  "storageKey": "docs/2026/06/DOC-1000-duplicate.pdf",
                  "checksum": "sha256:9c406998f735af15"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post()
                .then()
                .statusCode(409)
                .body("error", equalTo("Document DOC-1000 already exists"));
    }
}
