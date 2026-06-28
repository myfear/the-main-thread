package dev.mainthread.refunddesk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class RefundPolicyTest {

    private final RefundPolicy policy = new RefundPolicy();

    @Test
    void approvesSmallCleanRefund() {
        RefundCase refundCase = policy.evaluate(new RefundRequest(
                "refund-2001",
                "customer-1",
                new BigDecimal("25.00"),
                true,
                120,
                0));

        assertEquals(DecisionOutcome.APPROVED, refundCase.decision().outcome());
        assertEquals("small refund with clean history", refundCase.decision().reason());
    }

    @Test
    void deniesMissingReceipt() {
        RefundCase refundCase = policy.evaluate(new RefundRequest(
                "refund-2002",
                "customer-2",
                new BigDecimal("25.00"),
                false,
                120,
                0));

        assertEquals(DecisionOutcome.DENIED, refundCase.decision().outcome());
    }

    @Test
    void routesSuspiciousRefundToManualReview() {
        RefundCase refundCase = policy.evaluate(new RefundRequest(
                "refund-2003",
                "customer-3",
                new BigDecimal("450.00"),
                true,
                10,
                2));

        assertEquals(DecisionOutcome.MANUAL_REVIEW, refundCase.decision().outcome());
    }
}
