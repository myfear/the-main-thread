package dev.novadeck.tools.billing;

import dev.novadeck.tools.NovaDeckIds;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Billing and subscription tools for the NovaDeck ops demo catalog.
 */
@ApplicationScoped
public class BillingTools {

    @Tool("Fetch invoice header by invoice id.")
    public String getInvoice(String invoiceId) {
        return "invoice{" + invoiceId + ",amount_usd=1240.55,status=open}";
    }

    @Tool("List overdue invoices for an account id.")
    public String listOverdueInvoices(String accountId) {
        return "overdue[account=" + accountId + "]: " + NovaDeckIds.invoiceId("a1") + ","
                + NovaDeckIds.invoiceId("a2");
    }

    @Tool("Apply a percentage credit to an invoice with memo.")
    public String creditInvoice(String invoiceId, int percentCredit, String memo) {
        return "credit_applied{" + invoiceId + ",percent=" + percentCredit + ",memo=" + memo + "}";
    }

    @Tool("Return monthly spend trend label UP|FLAT|DOWN for account.")
    public String spendTrend(String accountId) {
        return "spend_trend{account=" + accountId + ",label=UP,yoy=12%}";
    }

    @Tool("Fetch payment method last4 for account.")
    public String paymentMethodLast4(String accountId) {
        return "pm{account=" + accountId + ",brand=visa,last4=4242}";
    }

    @Tool("Open a billing support case with subject line.")
    public String openBillingCase(String accountId, String subject) {
        return "case_opened{account=" + accountId + ",id=BILL-7712,subject=" + subject + "}";
    }

    @Tool("Estimate tax jurisdiction for account region code.")
    public String taxJurisdiction(String regionCode) {
        return "tax{region=" + regionCode + ",jurisdiction=EU-VAT,rate=21%}";
    }

    @Tool("Export usage CSV reference id for current billing period.")
    public String exportUsageCsv(String accountId) {
        return "usage_export{account=" + accountId + ",ref=csv-202605}";
    }
}
