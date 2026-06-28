package dev.mainthread.refunddesk;

import java.math.BigDecimal;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RefundPolicy {

    private static final BigDecimal AUTO_APPROVE_LIMIT = new BigDecimal("50.00");
    private static final BigDecimal MANUAL_REVIEW_LIMIT = new BigDecimal("300.00");

    public RefundCase evaluate(RefundRequest request) {
        if (!request.receiptPresent()) {
            return new RefundCase(request,
                    new RefundDecision(DecisionOutcome.DENIED, "receipt is missing"));
        }

        if (request.amount().compareTo(AUTO_APPROVE_LIMIT) <= 0 && request.chargebackCount() == 0) {
            return new RefundCase(request,
                    new RefundDecision(DecisionOutcome.APPROVED, "small refund with clean history"));
        }

        if (request.amount().compareTo(MANUAL_REVIEW_LIMIT) >= 0
                || request.accountAgeDays() < 30
                || request.chargebackCount() > 1) {
            return new RefundCase(request,
                    new RefundDecision(DecisionOutcome.MANUAL_REVIEW, "manual review required"));
        }

        return new RefundCase(request,
                new RefundDecision(DecisionOutcome.APPROVED, "standard refund approved"));
    }
}
