package com.themainthread.fernbank;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
class ProtocolBoundaryTest {

    private static final String PROTOCOL_VERSION = "2026-07-28";
    private static final String NAME_MISMATCH = "Header mismatch: Mcp-Name header value 'pptx_export' "
            + "does not match body value 'docs_generate'";

    @Test
    void rejectsUnauthenticatedRequestsBeforeProtocolParsing() {
        given()
                .contentType("application/json")
                .accept("application/json, text/event-stream")
                .body("{}")
                .when().post("/mcp")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "alice", roles = "content")
    void rejectsAHeaderBodyToolNameMismatch() {
        given()
                .contentType("application/json")
                .accept("application/json, text/event-stream")
                .header("Mcp-Protocol-Version", PROTOCOL_VERSION)
                .header("Mcp-Method", "tools/call")
                .header("Mcp-Name", "pptx_export")
                .body(Map.of(
                        "jsonrpc", "2.0",
                        "id", 1,
                        "method", "tools/call",
                        "params", Map.of(
                                "name", "docs_generate",
                                "arguments", Map.of(
                                        "topic", "Quarterly controls",
                                        "destinationTeam", "content"),
                                "_meta", statelessMetadata())))
                .when().post("/mcp")
                .then()
                .statusCode(400)
                .body("error.code", equalTo(-32020))
                .body("error.message", equalTo(NAME_MISMATCH));
    }

    private Map<String, Object> statelessMetadata() {
        return Map.of(
                "io.modelcontextprotocol/protocolVersion", PROTOCOL_VERSION,
                "io.modelcontextprotocol/clientInfo", Map.of(
                        "name", "fernbank-test-client",
                        "version", "1.0"),
                "io.modelcontextprotocol/clientCapabilities", Map.of());
    }
}
