package dev.novadeck.tools;

/**
 * Deterministic fake identifiers so traces and tests stay stable.
 */
public final class NovaDeckIds {

    private NovaDeckIds() {
    }

    public static String incidentId(String seed) {
        return "inc-" + seed;
    }

    public static String deployId(String seed) {
        return "dep-" + seed;
    }

    public static String invoiceId(String seed) {
        return "inv-" + seed;
    }

    public static String fleetNode(String seed) {
        return "node-" + seed;
    }

    public static String auditEvent(String seed) {
        return "aud-" + seed;
    }
}
