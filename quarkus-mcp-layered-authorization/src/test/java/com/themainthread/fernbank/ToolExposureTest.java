package com.themainthread.fernbank;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
class ToolExposureTest {

    @Test
    @TestSecurity(user = "alice", roles = { "content", "mcp-auditor" })
    void productionClientOnlySeesAdmittedTools() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();
        try {
            client.when()
                    .toolsList(page -> {
                        assertEquals(1, page.size());
                        assertNotNull(page.findByName("docs_generate"));
                        assertFalse(page.tools().stream()
                                .anyMatch(tool -> tool.name().equals("pptx_export")));
                        assertFalse(page.tools().stream()
                                .anyMatch(tool -> tool.name().equals("unsigned_status")));
                    })
                    .thenAssertResults();

            client.when()
                    .toolsCall("pptx_export")
                    .withArguments(Map.of("title", "Quarterly review"))
                    .withErrorAssert(error -> assertTrue(error.message().contains("pptx_export")))
                    .send()
                    .thenAssertResults();

            given()
                    .queryParam("limit", 20)
                    .when().get("/api/decisions")
                    .then()
                    .statusCode(200)
                    .body("skillId", hasItem("pptx_export"))
                    .body("reasonCodes.flatten()", hasItem("SCOPE_NOT_ALLOWED"))
                    .body("find { it.skillId == 'pptx_export' }.transientConnection", equalTo(true))
                    .body("find { it.skillId == 'pptx_export' }.requestId", not(blankOrNullString()));
        } finally {
            client.disconnect();
        }
    }
}
