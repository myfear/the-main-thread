package com.themainthread.releaseradar.api;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import org.junit.jupiter.api.Test;

@QuarkusTest
class IssueResourceTest {

    @Test
    void returnsOldHighSeverityBlockersInAgeOrder() {
        List<BlockerView> blockers = given()
                .queryParam("asOf", "2026-07-15T12:00:00")
                .queryParam("olderThanHours", 24)
                .queryParam("severity", "CRITICAL", "HIGH")
                .queryParam("limit", 20)
                .when()
                .get("/issues/blockers")
                .then()
                .statusCode(200)
                .extract()
                .as(new TypeRef<>() {
                });

        assertEquals(
                List.of("REL-106", "REL-101", "REL-102", "REL-107"),
                blockers.stream().map(BlockerView::key).toList());
    }

    @Test
    void groupsOpenIssuesByService() {
        List<ServiceHotspot> hotspots = given()
                .queryParam("minimumOpen", 2)
                .when()
                .get("/issues/hotspots")
                .then()
                .statusCode(200)
                .extract()
                .as(new TypeRef<>() {
                });

        assertEquals("payments", hotspots.getFirst().service());
        assertEquals(3L, hotspots.getFirst().openIssues());
        assertEquals(List.of("catalog", "search"),
                hotspots.subList(1, hotspots.size()).stream()
                        .map(ServiceHotspot::service)
                        .sorted()
                        .toList());
    }

    @Test
    void findsOpenIssuesAboveTheOpenIssueImpactAverage() {
        List<ImpactOutlier> outliers = given()
                .when()
                .get("/issues/outliers")
                .then()
                .statusCode(200)
                .extract()
                .as(new TypeRef<>() {
                });

        assertEquals(List.of("REL-104", "REL-101"),
                outliers.stream().map(ImpactOutlier::key).toList());
    }

    @Test
    void rejectsUnboundedPageSizes() {
        given()
                .queryParam("limit", 101)
                .when()
                .get("/issues/blockers")
                .then()
                .statusCode(400);
    }
}
