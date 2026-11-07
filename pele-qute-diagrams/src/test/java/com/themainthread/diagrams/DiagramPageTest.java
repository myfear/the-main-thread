package com.themainthread.diagrams;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DiagramPageTest {
    @Test
    void servesTheCatalogAndBundledAssets() {
        given().when().get("/").then().statusCode(200)
                .contentType("text/html")
                .body(containsString("Fulfillment Diagram Lab"))
                .body(containsString("flowchart LR"))
                .body(containsString("Stock available?"))
                .body(containsString("/static/bundle/app"));
    }

    @Test
    void escapesSourceAtTheHtmlBoundary() {
        String html = DiagramPage.Templates.diagrams("</textarea><script>alert('x')</script> & {value}").render();
        assertFalse(html.contains("<script>alert"));
        assertTrue(html.contains("&lt;/textarea&gt;"));
        assertTrue(html.contains("&amp; {value}"));
    }
}
