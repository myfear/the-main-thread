package dev.verdictiq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.verdictiq.ai.PanelAiInvoker;
import dev.verdictiq.model.ModelVerdict;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
@EnabledIfEnvironmentVariable(named = "VERDICTIQ_LIVE_OLLAMA", matches = "true")
class LivePanelistSmokeTest {

    @Inject
    PanelAiInvoker panelAiInvoker;

    @Test
    void graniteReturnsStructuredVerdict() {
        ModelVerdict verdict = panelAiInvoker.classifyWithGranite("I guess the service was fine, not terrible.");
        assertThat(verdict.label()).isNotNull();
        assertThat(verdict.reason()).isNotBlank();
    }

    @Test
    void mistralReturnsStructuredVerdict() {
        ModelVerdict verdict = panelAiInvoker.classifyWithMistral("I guess the service was fine, not terrible.");
        assertThat(verdict.label()).isNotNull();
        assertThat(verdict.reason()).isNotBlank();
    }
}
