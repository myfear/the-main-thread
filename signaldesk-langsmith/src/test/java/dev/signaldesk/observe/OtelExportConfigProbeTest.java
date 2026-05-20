package dev.signaldesk.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

class OtelExportConfigProbeTest {

    @Test
    void extractsProjectNameFromHeaders() {
        String headers = "x-api-key=secret,Langsmith-Project=signaldesk-langsmith";

        String project = OtelExportConfigProbe.resolveProject(ConfigProvider.getConfig(), headers);

        assertEquals("signaldesk-langsmith", project);
    }

    @Test
    void detectsApiKeyFromHeaders() {
        String headers = "x-api-key=secret,Langsmith-Project=signaldesk-langsmith";

        assertTrue(OtelExportConfigProbe.isApiKeyPresentInHeaders(headers));
    }

    @Test
    void ignoresMissingApiKeyInHeaders() {
        String headers = "x-api-key=,Langsmith-Project=signaldesk-langsmith";

        assertFalse(OtelExportConfigProbe.isApiKeyPresentInHeaders(headers));
    }

    @Test
    void detectsApiKeyFromConfigEnv() {
        // When LANGSMITH_API_KEY is exported in the shell (common during local mvn verify),
        // isApiKeySet should be true even if the header placeholder is empty.
        if (ConfigProvider.getConfig()
                .getOptionalValue("LANGSMITH_API_KEY", String.class)
                .filter(v -> !v.isBlank())
                .isPresent()) {
            assertTrue(
                    OtelExportConfigProbe.isApiKeySet(
                            ConfigProvider.getConfig(), "x-api-key=,Langsmith-Project=signaldesk-langsmith"));
        }
    }
}
