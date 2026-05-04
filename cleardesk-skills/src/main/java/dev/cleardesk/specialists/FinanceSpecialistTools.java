package dev.cleardesk.specialists;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.Tool;

/**
 * Stub tools for finance operations (billing, refunds, invoices).
 */
@ApplicationScoped
public class FinanceSpecialistTools {

    private static final Logger LOG = Logger.getLogger(FinanceSpecialistTools.class);

    @Tool("Requests a refund for a charge (finance scope only).")
    public String requestRefund(String chargeId, String reason) {
        LOG.debugf("refund %s: %s", chargeId, reason);
        return "finance:refund:" + chargeId;
    }

    @Tool("Looks up an invoice by id.")
    public String lookupInvoice(String invoiceId) {
        LOG.debugf("invoice lookup: %s", invoiceId);
        return "finance:invoice:" + invoiceId;
    }
}
