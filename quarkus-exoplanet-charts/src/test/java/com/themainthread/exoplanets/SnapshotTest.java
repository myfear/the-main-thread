package com.themainthread.exoplanets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SnapshotTest {
    @Test
    void servesAllChartsAsJsonObjectsWithConsistentTotals() {
        var json = given().when().get("/api/dashboard").then().statusCode(200)
                .contentType("application/json").header("Cache-Control", "no-store")
                .body("source", equalTo("snapshot"))
                .body("selected", greaterThan(0))
                .body("discoveries.type", equalTo("bar"))
                .body("methods.type", equalTo("doughnut"))
                .body("sizes.type", equalTo("scatter"))
                .extract().jsonPath();
        List<Integer> totals = json.getList("methods.data.datasets[0].data", Integer.class);
        assertEquals(json.getInt("selected"), totals.stream().mapToInt(Integer::intValue).sum());
        assertEquals(json.getInt("selected"), json.getInt("plotted") + json.getInt("excludedFromScatter"));
    }

    @Test
    void rejectsInvalidRangesAndSources() {
        for (String query : List.of("from=2020&to=2000", "from=abc", "source=unknown", "from=1900", "to=9999")) {
            given().when().get("/api/dashboard?" + query).then().statusCode(400)
                    .body("error", anyOf(containsString("Use"), containsString("Years")));
        }
    }

    @Test
    void servesGeneratedBundlesReferencedByThePage() {
        var html = given().get("/").then().statusCode(200).body(containsString("Worlds beyond"))
                .body(not(containsString("{#bundle"))).extract().htmlPath();
        List<String> scripts = html.getList("html.head.script.@src");
        List<String> styles = html.getList("html.head.link.findAll { it.@rel == 'stylesheet' }.@href");
        assertEquals(1, scripts.size());
        assertEquals(1, styles.size());
        String script = scripts.getFirst();
        String style = styles.getFirst();
        assertTrue(script.startsWith("/static/bundle/app") && script.endsWith(".js"));
        assertTrue(style.startsWith("/static/bundle/app") && style.endsWith(".css"));
        given().get(script).then().statusCode(200).contentType(containsString("javascript"))
                .body(containsString("4.5.1")).body(containsString("Planets shown in the scatter plot"));
        given().get(style).then().statusCode(200).contentType(containsString("css"))
                .body(containsString(".canvas-wrap"));
    }
}
