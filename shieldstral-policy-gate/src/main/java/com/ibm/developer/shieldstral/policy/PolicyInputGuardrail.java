package com.ibm.developer.shieldstral.policy;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;

abstract class PolicyInputGuardrail implements InputGuardrail {

    private final PolicyGate gate;
    private final PolicySurface surface;

    PolicyInputGuardrail(PolicyGate gate, PolicySurface surface) {
        this.gate = gate;
        this.surface = surface;
    }

    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        SafetyAssessment assessment = gate.evaluate(surface, PolicyDirection.INPUT, request.userMessage().singleText());
        return assessment.blocked() ? fatal(assessment.reason()) : success();
    }
}
