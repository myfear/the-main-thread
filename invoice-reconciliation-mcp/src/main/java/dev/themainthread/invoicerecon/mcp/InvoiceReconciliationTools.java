package dev.themainthread.invoicerecon.mcp;

import java.time.LocalDate;
import java.util.Optional;

import org.jboss.logging.Logger;

import dev.themainthread.invoicerecon.batch.ReconciliationBatchResult;
import dev.themainthread.invoicerecon.domain.MissingGoodsReceiptAction;
import dev.themainthread.invoicerecon.domain.ReconciliationOutcome;
import dev.themainthread.invoicerecon.policy.ReconciliationPolicy;
import dev.themainthread.invoicerecon.service.PostingService;
import dev.themainthread.invoicerecon.service.ReconciliationService;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InvoiceReconciliationTools {

    private static final Logger LOG = Logger.getLogger(InvoiceReconciliationTools.class);

    private static final double DEFAULT_MAXIMUM_VARIANCE_PERCENT = 2.5;
    private static final String DEFAULT_COST_CENTER = "FIN-OPERATIONS";
    private static final boolean DEFAULT_POST_MATCHED_INVOICES = false;
    private static final MissingGoodsReceiptAction DEFAULT_MISSING_GOODS_RECEIPT_ACTION =
            MissingGoodsReceiptAction.FLAG_FOR_REVIEW;

    @Inject
    ReconciliationService reconciliationService;

    @Inject
    PostingService postingService;

    @Blocking
    @Tool(
            name = "reconcile_invoices",
            description = """
                    Reconciles open supplier invoices against purchase orders and goods receipts.
                    Elicitation-capable clients are prompted for business policy interactively.
                    Other clients may pass the optional policy arguments below, or rely on tutorial defaults
                    (2.5% variance, FLAG_FOR_REVIEW, FIN-OPERATIONS).
                    """)
    public String reconcileInvoices(
            @ToolArg(description = "Supplier identifier from the ERP (seed data uses ACME for Acme Supplies)")
                    String supplierId,
            @ToolArg(description = "Start date in ISO-8601 format") LocalDate from,
            @ToolArg(description = "End date in ISO-8601 format") LocalDate to,
            @ToolArg(description = "Maximum price variance percent (0-20) when not using elicitation")
                    Optional<Double> maximumVariancePercent,
            @ToolArg(description = "Default cost center when not using elicitation") Optional<String> defaultCostCenter,
            @ToolArg(description = "Preference only; posting happens in post_reconciliation_batch")
                    Optional<Boolean> postMatchedInvoices,
            @ToolArg(description = "FLAG_FOR_REVIEW or REJECT_INVOICE when not using elicitation")
                    Optional<String> missingGoodsReceiptAction,
            Elicitation elicitation,
            Progress progress,
            Cancellation cancellation) {
        Optional<ReconciliationPolicy> policy = resolvePolicy(
                maximumVariancePercent,
                defaultCostCenter,
                postMatchedInvoices,
                missingGoodsReceiptAction,
                elicitation);
        if (policy.isEmpty()) {
            return ReconciliationResultJson.toJson(ReconciliationBatchResult.message(
                    ReconciliationOutcome.ELICITATION_DECLINED,
                    "Reconciliation policy was not provided"));
        }

        ReconciliationBatchResult result = reconciliationService.runBatch(
                supplierId,
                from,
                to,
                policy.get(),
                progress,
                cancellation);
        return ReconciliationResultJson.toJson(result);
    }

    private Optional<ReconciliationPolicy> resolvePolicy(
            Optional<Double> maximumVariancePercent,
            Optional<String> defaultCostCenter,
            Optional<Boolean> postMatchedInvoices,
            Optional<String> missingGoodsReceiptAction,
            Elicitation elicitation) {
        if (hasInlinePolicy(
                maximumVariancePercent, defaultCostCenter, postMatchedInvoices, missingGoodsReceiptAction)) {
            return Optional.of(policyFromArgs(
                    maximumVariancePercent, defaultCostCenter, postMatchedInvoices, missingGoodsReceiptAction));
        }

        if (!elicitation.isSupported()) {
            LOG.info("Client does not support elicitation; using tutorial default reconciliation policy");
            return Optional.of(tutorialDefaultPolicy());
        }

        ElicitationRequest request = elicitation.requestBuilder()
                .setMessage("Provide the invoice reconciliation policy")
                .addSchemaProperty(
                        "maximumVariancePercent",
                        ElicitationRequest.NumberSchema.builder()
                                .setTitle("Maximum price variance")
                                .setDescription("Invoices above this percentage are flagged")
                                .setMinimum(0)
                                .setMaximum(20)
                                .setDefaultValue(2.5)
                                .build())
                .addSchemaProperty(
                        "defaultCostCenter",
                        ElicitationRequest.StringSchema.builder()
                                .setTitle("Default cost center")
                                .setDescription("Used when the invoice has no cost center")
                                .setMinLength(3)
                                .setMaxLength(30)
                                .setDefaultValue("FIN-OPERATIONS")
                                .build())
                .addSchemaProperty(
                        "postMatchedInvoices",
                        ElicitationRequest.BooleanSchema.builder()
                                .setTitle("Post matched invoices")
                                .setDescription("Preference only; posting happens in post_reconciliation_batch")
                                .setDefaultValue(false)
                                .build())
                .addSchemaProperty(
                        "missingGoodsReceiptAction",
                        ElicitationRequest.StringSchema.builder()
                                .setTitle("Missing goods receipt")
                                .setDescription("Use FLAG_FOR_REVIEW or REJECT_INVOICE")
                                .setDefaultValue("FLAG_FOR_REVIEW")
                                .build())
                .build();

        var response = request.sendAndAwait();
        if (!response.actionAccepted()) {
            LOG.info("Reconciliation policy elicitation declined");
            return Optional.empty();
        }

        return Optional.of(new ReconciliationPolicy(
                response.content().getNumber("maximumVariancePercent").doubleValue(),
                parseMissingGoodsReceiptAction(response.content().getString("missingGoodsReceiptAction")),
                response.content().getBoolean("postMatchedInvoices"),
                response.content().getString("defaultCostCenter")));
    }

    private boolean hasInlinePolicy(
            Optional<Double> maximumVariancePercent,
            Optional<String> defaultCostCenter,
            Optional<Boolean> postMatchedInvoices,
            Optional<String> missingGoodsReceiptAction) {
        return maximumVariancePercent.isPresent()
                || defaultCostCenter.isPresent()
                || postMatchedInvoices.isPresent()
                || missingGoodsReceiptAction.isPresent();
    }

    private ReconciliationPolicy policyFromArgs(
            Optional<Double> maximumVariancePercent,
            Optional<String> defaultCostCenter,
            Optional<Boolean> postMatchedInvoices,
            Optional<String> missingGoodsReceiptAction) {
        return new ReconciliationPolicy(
                maximumVariancePercent.orElse(DEFAULT_MAXIMUM_VARIANCE_PERCENT),
                parseMissingGoodsReceiptAction(missingGoodsReceiptAction.orElse(DEFAULT_MISSING_GOODS_RECEIPT_ACTION.name())),
                postMatchedInvoices.orElse(DEFAULT_POST_MATCHED_INVOICES),
                defaultCostCenter.orElse(DEFAULT_COST_CENTER));
    }

    private ReconciliationPolicy tutorialDefaultPolicy() {
        return new ReconciliationPolicy(
                DEFAULT_MAXIMUM_VARIANCE_PERCENT,
                DEFAULT_MISSING_GOODS_RECEIPT_ACTION,
                DEFAULT_POST_MATCHED_INVOICES,
                DEFAULT_COST_CENTER);
    }

    @Blocking
    @Tool(
            name = "post_reconciliation_batch",
            description = "Posts matched invoices from an approved reconciliation batch to the ledger")
    public String postReconciliationBatch(@ToolArg(description = "Reconciliation batch identifier") String batchId) {
        return postingService.postBatch(batchId);
    }

    private MissingGoodsReceiptAction parseMissingGoodsReceiptAction(String value) {
        if (value == null || value.isBlank()) {
            return MissingGoodsReceiptAction.FLAG_FOR_REVIEW;
        }
        return MissingGoodsReceiptAction.valueOf(value);
    }
}
