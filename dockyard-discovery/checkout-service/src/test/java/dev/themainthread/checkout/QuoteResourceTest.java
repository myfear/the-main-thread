package dev.themainthread.checkout;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(FakeConsulCatalogRegistry.class)
@TestHTTPEndpoint(QuoteResource.class)
class QuoteResourceTest {

    @BeforeEach
    void resetRegistry() {
        FakeConsulCatalogRegistry.exposeBothInstances();
    }

    @Test
    void resolvesQuoteFromConsulBackedStorkDiscovery() {
        given()
                .when().get("/sku-1")
                .then()
                .statusCode(200)
                .body("sku", equalTo("sku-1"))
                .body("price", equalTo(19.99F))
                .body("instanceId", anyOf(equalTo("catalog-1"), equalTo("catalog-2")))
                .body("color", anyOf(equalTo("blue"), equalTo("green")))
                .body("servedAt", notNullValue());
    }

    @Test
    void usesBothInstancesWithRoundRobinSelection() {
        Set<String> seen = new LinkedHashSet<>();

        for (int i = 0; i < 6; i++) {
            String instanceId = given()
                    .when().get("/sku-1")
                    .then()
                    .statusCode(200)
                    .extract()
                    .path("instanceId");
            seen.add(instanceId);
        }

        assertEquals(Set.of("catalog-1", "catalog-2"), seen);
    }

    @Test
    void fallsBackToTheRemainingInstanceWhenOneDisappears() throws InterruptedException {
        given()
                .when().get("/sku-1")
                .then()
                .statusCode(200);

        FakeConsulCatalogRegistry.exposeOnlyCatalogOne();
        Thread.sleep(1100);

        for (int i = 0; i < 4; i++) {
            String instanceId = given()
                    .when().get("/sku-1")
                    .then()
                    .statusCode(200)
                    .extract()
                    .path("instanceId");
            assertTrue(instanceId.equals("catalog-1"));
        }
    }
}
