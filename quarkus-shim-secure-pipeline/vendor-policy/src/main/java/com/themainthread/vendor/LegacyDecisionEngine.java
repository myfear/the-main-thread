package com.themainthread.vendor;

/**
 * Simulates a vendor class that we cannot change during an incident.
 */
public final class LegacyDecisionEngine {

    private LegacyDecisionEngine() {
    }

    /**
     * Returns whether an upstream policy decision allows access.
     *
     * @param decision the decision returned by the upstream policy system
     * @return {@code true} unless the upstream system explicitly denied access
     */
    public static boolean isAllowed(String decision) {
        return !"DENY".equalsIgnoreCase(decision);
    }
}
