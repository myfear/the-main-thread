package com.ibm.developer.shieldstral.policy;

import jakarta.inject.Singleton;

@Singleton
public final class SecurityResearchOutputGuardrail extends PolicyOutputGuardrail {

    SecurityResearchOutputGuardrail(PolicyGate gate) {
        super(gate, PolicySurface.SECURITY_RESEARCH);
    }
}
