package dev.novadeck.tools.utility;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Small always-useful helpers (also part of the 50-tool catalog).
 */
@ApplicationScoped
public class UtilityTools {

    @Tool("Return current synthetic NovaDeck environment label (prod|staging).")
    public String currentEnvironmentLabel() {
        return "env=prod";
    }

    @Tool("Echo-ping for connectivity checks with token.")
    public String ping(String token) {
        return "pong:" + token;
    }

    @Tool("Show on-call rotation handle for a team name.")
    public String onCallHandle(String teamName) {
        return "oncall{team=" + teamName + ",pagerduty=PY4321}";
    }

    @Tool("Resolve internal wiki URL slug for a topic keyword.")
    public String wikiSlug(String topicKeyword) {
        return "wiki_slug{" + topicKeyword + " -> novadeck/runbooks/" + topicKeyword.toLowerCase() + "}";
    }

    @Tool("Return regional maintenance calendar id string.")
    public String maintenanceCalendarId(String regionCode) {
        return "maint_cal{" + regionCode + ",id=cal-eu-west-01}";
    }

    @Tool("Format a UTC timestamp label for audit-friendly logging.")
    public String utcTimestampLabel() {
        return "utc=2026-05-04T12:00:00Z";
    }

    @Tool("Validate incident id format without fetching remote state.")
    public String validateIncidentIdFormat(String incidentId) {
        boolean ok = incidentId != null && incidentId.startsWith("inc-");
        return "valid=" + ok;
    }

    @Tool("Short usage hint listing NovaDeck capability areas.")
    public String capabilityOverview() {
        return "areas=[incidents,deploys,billing,fleet,audit,utility]";
    }
}
