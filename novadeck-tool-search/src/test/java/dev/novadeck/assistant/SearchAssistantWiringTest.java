package dev.novadeck.assistant;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import jakarta.inject.Inject;

@QuarkusTest
class SearchAssistantWiringTest {

    @Inject
    SearchAssistantClient searchAssistantClient;

    @Inject
    FixedOpsAssistant fixedOpsAssistant;

    @Test
    void bothAssistantWiringPresent() {
        assertNotNull(fixedOpsAssistant);
        assertNotNull(searchAssistantClient);
    }
}
