package com.themainthread.progress.domain;

import java.math.BigDecimal;

public record InvoiceRow(String invoiceNumber, BigDecimal amount, String currency) {
}
