package dev.mainthread.openui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class StdioBobConnectionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void cancellationBeforeInitializationDoesNotStartBob() throws Exception {
        BobConfig config = new BobConfig() {
            public String binary() {
                return "/nonexistent-bob-executable";
            }

            public Optional<String> keyFile() {
                return Optional.empty();
            }

            public Optional<String> apiKey() {
                return Optional.empty();
            }

            public Duration promptTimeout() {
                return Duration.ofSeconds(1);
            }
        };
        try (var connection = new StdioBobConnection(config, "test-placeholder")) {
            connection.close();
            var failure = assertThrows(IllegalStateException.class, connection::initialize);
            assertEquals("Bob connection is closed", failure.getMessage());
        }
    }

    @Test
    void refusesToolPermissionsEvenWhenAllowOptionsAreAdvertised() throws Exception {
        var request = mapper.readTree("""
                {"id":"permission-1","method":"session/request_permission","params":{
                    "options":[{"kind":"allow_always","optionId":"yes"}],
                    "toolCall":{"title":"Run shell command"}}}
                """);
        var result = StdioBobConnection.clientResponse(mapper, request);
        assertEquals("permission-1", result.path("id").asText());
        assertEquals("cancelled", result.path("result").path("outcome").path("outcome").asText());
        assertFalse(result.toString().contains("allow"));
    }

    @Test
    void declinesUnadvertisedClientCapabilities() throws Exception {
        var request = mapper.readTree("{\"id\":7,\"method\":\"fs/read_text_file\"}");
        assertEquals(-32601, StdioBobConnection.clientResponse(mapper, request).path("error").path("code").asInt());
    }
}
