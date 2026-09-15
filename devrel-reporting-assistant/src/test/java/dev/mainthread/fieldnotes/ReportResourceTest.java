package dev.mainthread.fieldnotes;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

@QuarkusTest
@Timeout(15)
class ReportResourceTest {
    @Inject
    ReportService reports;

    @Inject
    ObjectMapper mapper;

    @BeforeEach
    @AfterEach
    void resetTestReport() {
        reports.reset();
    }

    @Test
    void createReadAndResetRoundTripWithoutStartingBob() {
        request().get("/api/report").then().statusCode(404);
        String id = request().body(Map.of("month", "2026-09"))
                .post("/api/report").then().statusCode(200)
                .body("id", notNullValue())
                .body("month", equalTo("2026-09"))
                .body("values.author", equalTo("Taylor Quinn"))
                .body("revision", equalTo(0))
                .body("running", equalTo(false))
                .body("status", equalTo("incomplete"))
                .extract().path("id");
        request().get("/api/report").then().statusCode(200).body("id", equalTo(id));
        request().body(Map.of("month", "2026-09")).post("/api/report").then().statusCode(409);
        request().delete("/api/report").then().statusCode(200).body("status", equalTo("reset"));
        request().get("/api/report").then().statusCode(404);
        request().body(Map.of("month", "2026-09", "author", "Jordan Vale"))
                .post("/api/report").then().statusCode(200).body("values.author", equalTo("Jordan Vale"));
    }

    @Test
    void incompleteReportCannotBeSavedOrDownloadedAndUnknownFormCannotUpdateIt() {
        createReport();
        request().body(Map.of("expectedRevision", 0)).post("/api/report/save").then().statusCode(409);
        request().get("/api/report/download").then().statusCode(409);
        request().body(Map.of("expectedRevision", 0, "values", Map.of("title", "Late answer")))
                .post("/api/report/forms/unknown/answers").then().statusCode(409);
        request().get("/api/report").then().statusCode(200)
                .body("revision", equalTo(0)).body("status", equalTo("incomplete"));
    }

    @Test
    void invalidAuthorAndMissingMonthAreRejectedWithoutCreatingReport() {
        request().body(Map.of("author", "Taylor Quinn")).post("/api/report").then().statusCode(400);
        request().body(Map.of("month", "not-a-month")).post("/api/report").then().statusCode(400);
        request().body(Map.of("month", "2026-09", "author", "Unknown Person"))
                .post("/api/report").then().statusCode(400);
        request().body(Map.of("month", "2026-09", "author", "   "))
                .post("/api/report").then().statusCode(400);
        request().get("/api/report").then().statusCode(404);
    }

    @Test
    void mcpToolCallsWithoutReportCredentialCannotReadOrMutateDraft() throws Exception {
        createReport();
        Response initialized = request().accept("application/json, text/event-stream")
                .body(Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params",
                        Map.of("protocolVersion", "2025-03-26", "capabilities", Map.of(),
                                "clientInfo", Map.of("name", "field-notes-test", "version", "1.0"))))
                .post("/mcp").then().statusCode(200).extract().response();
        String mcpSession = initialized.header("Mcp-Session-Id");
        assertNotNull(mcpSession, "MCP initialize must establish a transport session");
        request().accept("application/json, text/event-stream")
                .header("Mcp-Session-Id", mcpSession)
                .body(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"))
                .post("/mcp").then().statusCode(202);

        JsonNode read = callTool(mcpSession, 2, "getReport", Map.of());
        assertToolDenied(read);
        JsonNode update = callTool(mcpSession, 3, "updateReport",
                Map.of("expectedRevision", 0, "values", Map.of("title", "Unauthorized mutation")));
        assertToolDenied(update);
        request().get("/api/report").then().statusCode(200).body("revision", equalTo(0));
        assertFalse(((Map<?, ?>) reports.current().view().get("values")).containsKey("title"));
    }

    private JsonNode callTool(String session, int id, String name, Map<String, Object> arguments) throws Exception {
        Response response = request().accept("application/json, text/event-stream")
                .header("Mcp-Session-Id", session)
                .body(Map.of("jsonrpc", "2.0", "id", id, "method", "tools/call",
                        "params", Map.of("name", name, "arguments", arguments)))
                .post("/mcp").then().statusCode(200).extract().response();
        String body = response.asString();
        if (response.contentType().contains("text/event-stream")) {
            body = body.lines().filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).trim()).findFirst().orElseThrow();
        }
        return mapper.readTree(body);
    }

    private static void assertToolDenied(JsonNode response) {
        assertTrue(response.has("error") || response.path("result").path("isError").asBoolean(),
                () -> "Expected MCP tool denial, got " + response);
        assertFalse(response.toString().contains("Taylor Quinn"), "Denied tool must not leak report values");
    }

    private static void createReport() {
        request().body(Map.of("month", "2026-09")).post("/api/report").then().statusCode(200);
    }

    private static RequestSpecification request() {
        return given().config(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", 3000).setParam("http.socket.timeout", 5000)))
                .contentType("application/json");
    }
}
