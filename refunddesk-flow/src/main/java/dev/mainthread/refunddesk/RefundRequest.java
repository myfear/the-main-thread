package dev.mainthread.refunddesk;

import java.math.BigDecimal;

public record RefundRequest(
        String refundId,
        String customerId,
        BigDecimal amount,
        boolean receiptPresent,
        int accountAgeDays,
        int chargebackCount) {
}
