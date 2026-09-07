package com.themainthread.exoplanets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertSame;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(value = ArchiveStub.class, restrictToAnnotatedClass = true)
class LiveCatalogueTest {
    @Inject
    CatalogueService catalogues;

    @Test
    void callsTapAndDeserializesItsColumnNames() {
        given().queryParam("source", "live").when().get("/api/dashboard").then().statusCode(200)
                .body("selected", equalTo(1)).body("plotted", equalTo(1))
                .body("sizes.data.datasets[0].data[0].name", equalTo("Fixture b"));
    }

    @Test
    void reusesTheCachedCatalogueAcrossRequests() {
        assertSame(catalogues.load(CatalogueService.Source.live), catalogues.load(CatalogueService.Source.live));
    }
}
