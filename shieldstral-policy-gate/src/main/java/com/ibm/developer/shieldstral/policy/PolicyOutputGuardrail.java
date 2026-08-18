package com.ibm.developer.shieldstral.policy;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;

abstract class PolicyOutputGuardrail implements OutputGuardrail {

    private final PolicyGate gate;
    private final PolicySurface surface;

    PolicyOutputGuardrail(PolicyGate gate, PolicySurface surface) {
        this.gate = gate;
        this.surface = surface;
    }

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        String responseText = request.responseFromLLM().aiMessage().text();
        SafetyAssessment assessment = gate.evaluate(surface, PolicyDirection.OUTPUT, responseText);
        return assessment.blocked() ? fatal(assessment.reason()) : success();
    }
}
