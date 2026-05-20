package dev.themainthread.meridian;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ArticlesResourceTest {

    @Test
    void rootExposesDiscoveryLinks() {
        List<String> links = given()
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .body("service", equalTo("Meridian Knowledge API"))
                .extract()
                .headers()
                .getValues("Link");

        assertTrue(links.stream().anyMatch(link -> link.contains("rel=\"api-catalog\"")));
        assertTrue(links.stream().anyMatch(link -> link.contains("rel=\"service-desc\"")));
        assertTrue(links.stream().anyMatch(link -> link.contains("rel=\"mcp-server-card\"")));
        assertTrue(links.stream().anyMatch(link -> link.contains("rel=\"agent-skills\"")));
    }

    @Test
    void listArticlesReturnsOk() {
        given().when().get("/api/v1/articles").then().statusCode(200);
    }

    @Test
    void staticDiscoveryFilesAreAvailable() {
        given()
                .when()
                .get("/robots.txt")
                .then()
                .statusCode(200)
                .body(containsString("Content-Signal: search=yes, ai-input=yes, ai-train=no"))
                .body(containsString("Sitemap: https://api.meridian.dev/sitemap.xml"));

        given()
                .when()
                .get("/llms.txt")
                .then()
                .statusCode(200)
                .body(containsString("MCP Server Card"))
                .body(containsString("Agent Skills index"));
    }

    @Test
    void markdownContentNegotiation() {
        given()
                .header("Accept", "text/markdown")
                .when()
                .get("/api/v1/articles/intro-to-meridian/content")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("text/markdown"))
                .header("Vary", "Accept")
                .body(containsString("# Introduction"));
    }

    @Test
    void apiCatalogIsLinksetJson() {
        given()
                .when()
                .get("/.well-known/api-catalog")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("application/linkset+json"))
                .body(containsString("/q/openapi?format=json"));
    }

    @Test
    void mcpServerCardIsDiscoverable() {
        given()
                .when()
                .get("/.well-known/mcp/server-card.json")
                .then()
                .statusCode(200)
                .body("serverInfo.name", equalTo("meridian"))
                .body("transport.type", equalTo("streamable-http"))
                .body("authentication.required", equalTo(true))
                .body("tools[0].name", equalTo("searchArticles"));

        given().when().get("/.well-known/mcp.json").then().statusCode(200);
    }

    @Test
    void agentSkillsIndexAndSkillAreDiscoverable() {
        given()
                .when()
                .get("/.well-known/agent-skills/index.json")
                .then()
                .statusCode(200)
                .body("$schema", equalTo("https://schemas.agentskills.io/discovery/0.2.0/schema.json"))
                .body("skills[0].name", equalTo("meridian"))
                .body("skills[0].type", equalTo("skill-md"))
                .body("skills[0].digest", startsWith("sha256:"));

        given()
                .when()
                .get("/.well-known/agent-skills/meridian/SKILL.md")
                .then()
                .statusCode(200)
                .header("Content-Type", containsString("text/markdown"))
                .body(containsString("name: meridian"))
                .body(containsString("Accept: text/markdown"));
    }

    @Test
    void postWithoutTokenReturnsUnauthorized() {
        given()
                .contentType("application/json")
                .body("{\"title\":\"Draft\",\"content\":\"No token yet\"}")
                .when()
                .post("/api/v1/articles")
                .then()
                .statusCode(401);
    }
}
