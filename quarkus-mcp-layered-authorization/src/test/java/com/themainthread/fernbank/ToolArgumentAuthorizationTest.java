package com.themainthread.fernbank;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

@QuarkusTest
class ToolArgumentAuthorizationTest {

    @Test
    @TestSecurity(user = "alice", roles = "content")
    void rejectsAValidToolCallForAnotherTeam() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();
        try {
            client.when()
                    .toolsCall("docs_generate")
                    .withArguments(Map.of(
                            "topic", "Quarterly controls",
                            "destinationTeam", "platform"))
                    .withAssert(response -> {
                        assertTrue(response.isError());
                        assertTrue(response.firstContent().asText().text().contains("platform"));
                    })
                    .send()
                    .thenAssertResults();
        } finally {
            client.disconnect();
        }
    }

    @Test
    @TestSecurity(user = "alice", roles = "content")
    void acceptsAValidToolCallForTheCallersTeam() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();
        try {
            client.when()
                    .toolsCall("docs_generate")
                    .withArguments(Map.of(
                            "topic", "Quarterly controls",
                            "destinationTeam", "content"))
                    .withAssert(response -> assertTrue(response.firstContent().asText().text().contains("content")))
                    .send()
                    .thenAssertResults();
        } finally {
            client.disconnect();
        }
    }
}
