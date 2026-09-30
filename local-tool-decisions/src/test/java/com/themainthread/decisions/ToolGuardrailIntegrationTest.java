package com.themainthread.decisions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.invocation.InvocationParameters;
import io.quarkiverse.langchain4j.guardrails.ToolGuardrailException;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(DecisionApiStub.class)
class ToolGuardrailIntegrationTest {
    private static final String USER_REQUEST = "Please refund EUR 19.99 for order 4712.";

    @Inject
    TestRefundAssistant assistant;

    @Inject
    DemoTools tools;

    @BeforeEach
    void reset() {
        tools.reset();
        DecisionApiStub.reset();
        TestRefundAssistant.ModelSupplier.reset();
    }

    @Test
    void allowsTheActualAiServiceToolInvocationAfterAssessment() {
        String result = assistant.chat(UUID.randomUUID().toString(), USER_REQUEST, context());

        assertEquals("Refund tool completed.", result);
        assertEquals(Map.of("4712", 1999L), tools.refunds());
        assertEquals(1, DecisionApiStub.requests().size());
        assertEquals(2, TestRefundAssistant.ModelSupplier.calls(), "Tool result should reach the chat model");
    }

    @Test
    void stopsTheActualAiServiceToolInvocationWhenDenied() {
        DecisionApiStub.answer("ORDER_LOOKUP", 0.98, 0.01, 0.01, 0.01);
        assertFatalBeforeMutation(context(), 1);
    }

    @Test
    void stopsTheActualAiServiceToolInvocationWhenReviewIsRequired() {
        DecisionApiStub.answer("REFUND", 0.01, 0.98, 0.01, 0.60);
        assertFatalBeforeMutation(context(), 1);
    }

    @Test
    void stopsBeforeTheDecisionModelWhenTheOriginalRequestIsMissing() {
        assertFatalBeforeMutation(new InvocationParameters(), 0);
    }

    private void assertFatalBeforeMutation(InvocationParameters parameters, int expectedDecisionCalls) {
        ToolGuardrailException failure = assertThrows(ToolGuardrailException.class,
                () -> assistant.chat(UUID.randomUUID().toString(), USER_REQUEST, parameters));

        assertTrue(failure.isFatal());
        assertTrue(tools.refunds().isEmpty());
        assertEquals(expectedDecisionCalls, DecisionApiStub.requests().size());
        assertEquals(1, TestRefundAssistant.ModelSupplier.calls(), "A fatal guardrail must stop the tool loop");
    }

    private InvocationParameters context() {
        return InvocationParameters.from("userRequest", USER_REQUEST);
    }
}
