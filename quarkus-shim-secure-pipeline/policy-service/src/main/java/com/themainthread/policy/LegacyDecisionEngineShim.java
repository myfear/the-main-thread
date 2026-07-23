package com.themainthread.policy;

import com.themainthread.vendor.LegacyDecisionEngine;

import io.quarkiverse.shim.Shim;
import io.quarkiverse.shim.ShimReplace;

@Shim(value = LegacyDecisionEngine.class, name = "fail-closed-decision")
public final class LegacyDecisionEngineShim {

    private LegacyDecisionEngineShim() {
    }

    @ShimReplace(method = "isAllowed", paramTypes = String.class)
    public static boolean isAllowed(String decision) {
        return "ALLOW".equalsIgnoreCase(decision);
    }
}
