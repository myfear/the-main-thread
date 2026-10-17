package com.themainthread.networkfaults;

import java.math.BigDecimal;

public record CarrierQuote(String destination, BigDecimal amount, String currency) {
}
