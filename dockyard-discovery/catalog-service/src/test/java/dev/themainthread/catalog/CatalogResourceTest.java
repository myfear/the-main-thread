package dev.themainthread.catalog;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestHTTPEndpoint(CatalogResource.class)
class CatalogResourceTest {

    @Inject
    CatalogInstanceConfig instance;

    @Test
    void returnsCatalogEntryWithInstanceMetadata() {
        given()
                .when().get("/sku-1")
                .then()
                .statusCode(200)
                .body("sku", equalTo("sku-1"))
                .body("price", equalTo(19.99F))
                .body("instanceId", equalTo(instance.id()))
                .body("color", equalTo(instance.color()))
                .body("servedAt", notNullValue());
    }
}
