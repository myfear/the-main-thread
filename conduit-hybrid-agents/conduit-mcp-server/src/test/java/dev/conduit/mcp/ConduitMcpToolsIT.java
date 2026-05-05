package dev.conduit.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ConduitMcpToolsIT {

    @Test
    void normalizeRecordToolReturnsCanonicalToken() {
        try (McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/mcp")
                .build()
                .connect()) {
            client.when()
                    .toolsCall("conduit_normalize_record")
                    .withArguments(Map.of("rawId", "  abc-42\t"))
                    .withAssert(response -> assertEquals("ABC-42", response.firstContent().asText().text()))
                    .send()
                    .thenAssertResults();
        }
    }

    @Test
    void fingerprintPayloadReturnsStableSha256Hex() {
        try (McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/mcp")
                .build()
                .connect()) {
            client.when()
                    .toolsCall("conduit_fingerprint_payload")
                    .withArguments(Map.of("payload_snippet", "{\"event\":\"demo\"}"))
                    .withAssert(response -> {
                        assertFalse(response.isError());
                        String hex = response.firstContent().asText().text();
                        assertEquals(64, hex.length());
                    })
                    .send()
                    .thenAssertResults();
        }
    }
}