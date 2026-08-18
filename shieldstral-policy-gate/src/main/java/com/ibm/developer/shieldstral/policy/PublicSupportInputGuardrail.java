package com.ibm.developer.shieldstral.policy;

import jakarta.inject.Singleton;

@Singleton
public final class PublicSupportInputGuardrail extends PolicyInputGuardrail {

    PublicSupportInputGuardrail(PolicyGate gate) {
        super(gate, PolicySurface.PUBLIC_SUPPORT);
    }
}
