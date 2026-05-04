package dev.novadeck.tools.audit;

import dev.novadeck.tools.NovaDeckIds;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Audit trail tools for the NovaDeck ops demo catalog.
 */
@ApplicationScoped
public class AuditTools {

    @Tool("Search audit events by actor email substring.")
    public String auditEventsByActor(String actorEmailSubstring) {
        return "audit[actor~=" + actorEmailSubstring + "]: " + NovaDeckIds.auditEvent("e1") + ","
                + NovaDeckIds.auditEvent("e2");
    }

    @Tool("Fetch audit event payload by event id.")
    public String getAuditEvent(String eventId) {
        return "audit_event{" + eventId + ",action=role.change,ip=203.0.113.10}";
    }

    @Tool("Verify policy gate PASS|FAIL for action name.")
    public String policyGateCheck(String actionName) {
        return "policy{action=" + actionName + ",result=PASS}";
    }

    @Tool("List retention policy days for audit logs by tenant id.")
    public String auditRetentionDays(String tenantId) {
        return "retention{tenant=" + tenantId + ",days=400}";
    }

    @Tool("Export immutable audit bundle reference for compliance ticket.")
    public String exportAuditBundle(String ticketId) {
        return "bundle{ticket=" + ticketId + ",ref=sha256:deadbeef}";
    }

    @Tool("Confirm segregation-of-duties check between two actors.")
    public String sodCheck(String actorA, String actorB) {
        return "sod{actors=[" + actorA + "," + actorB + "],violation=false}";
    }

    @Tool("Lookup data-access classification for a dataset tag.")
    public String datasetClassification(String datasetTag) {
        return "classification{dataset=" + datasetTag + ",level=INTERNAL}";
    }

    @Tool("Summarize privileged commands executed in last window hours.")
    public String privilegedCommandsSummary(int windowHours) {
        return "priv_summary{hours=" + windowHours + ",count=2,kinds=[sudo,reconfigure]}";
    }
}
