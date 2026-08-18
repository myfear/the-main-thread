package com.ibm.developer.shieldstral.policy;

import jakarta.inject.Singleton;

@Singleton
public final class SecurityResearchInputGuardrail extends PolicyInputGuardrail {

    SecurityResearchInputGuardrail(PolicyGate gate) {
        super(gate, PolicySurface.SECURITY_RESEARCH);
    }
}
