package com.ibm.developer.shieldstral.policy;

import jakarta.inject.Singleton;

@Singleton
public final class PublicSupportOutputGuardrail extends PolicyOutputGuardrail {

    PublicSupportOutputGuardrail(PolicyGate gate) {
        super(gate, PolicySurface.PUBLIC_SUPPORT);
    }
}
