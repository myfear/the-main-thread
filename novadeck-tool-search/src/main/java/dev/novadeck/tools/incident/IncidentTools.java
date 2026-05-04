package dev.novadeck.tools.incident;

import dev.novadeck.tools.NovaDeckIds;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Incident-management tools for the NovaDeck ops demo catalog.
 */
@ApplicationScoped
public class IncidentTools {

    @Tool("List active incidents filtered by severity (SEV1..SEV4). Returns a short summary line.")
    public String listActiveIncidents(String severity) {
        return "incidents[severity=" + severity + "]: SEV2-db-cache-exhaustion(id="
                + NovaDeckIds.incidentId("sev2") + "), SEV3-api-latency-spike(id="
                + NovaDeckIds.incidentId("sev3") + ")";
    }

    @Tool("Fetch full incident record by incident id.")
    public String getIncident(String incidentId) {
        return "incident{" + incidentId + ",title=api-errors,status=open,owner=nova-oncall}";
    }

    @Tool("Assign an incident to an on-call engineer by email.")
    public String assignIncident(String incidentId, String engineerEmail) {
        return "assigned{" + incidentId + ",to=" + engineerEmail + ",at=2026-05-04T10:00:00Z}";
    }

    @Tool("Resolve incident with resolution summary text.")
    public String resolveIncident(String incidentId, String resolutionSummary) {
        return "resolved{" + incidentId + ",summary=" + resolutionSummary + "}";
    }

    @Tool("Append an internal timeline note to an incident.")
    public String addIncidentNote(String incidentId, String noteText) {
        return "note_added{" + incidentId + ",chars=" + noteText.length() + "}";
    }

    @Tool("Return SLA breach risk for an incident as LOW|MEDIUM|HIGH.")
    public String incidentSlaRisk(String incidentId) {
        return "sla_risk{" + incidentId + ",level=MEDIUM,minutes_remaining=37}";
    }

    @Tool("Escalate incident to next tier with reason.")
    public String escalateIncident(String incidentId, String reason) {
        return "escalated{" + incidentId + ",tier=2,reason=" + reason + "}";
    }

    @Tool("Link incident to a deployment id for correlation.")
    public String linkIncidentToDeploy(String incidentId, String deployId) {
        return "linked{" + incidentId + ",deploy=" + deployId + "}";
    }

    @Tool("Aggregate incident counts per service name for the last 24h window.")
    public String incidentCountsByService(String serviceName) {
        return "incident_counts{service=" + serviceName + ",total=3,sev2=1,sev3=2}";
    }
}
