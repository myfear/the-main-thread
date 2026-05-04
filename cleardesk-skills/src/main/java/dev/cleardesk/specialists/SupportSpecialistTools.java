package dev.cleardesk.specialists;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.Tool;

/**
 * Stub tools for the support specialist (narrow scope: tickets and user-visible incidents).
 */
@ApplicationScoped
public class SupportSpecialistTools {

    private static final Logger LOG = Logger.getLogger(SupportSpecialistTools.class);

    @Tool("Creates a support intake record for triage (support scope only).")
    public String recordSupportIntake(String summary) {
        LOG.debugf("support intake: %s", summary);
        return "support:intake:" + summary;
    }

    @Tool("Looks up a ticket id in the mock support system.")
    public String lookupTicket(String ticketId) {
        LOG.debugf("ticket lookup: %s", ticketId);
        return "support:ticket:" + ticketId;
    }
}
