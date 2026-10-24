package com.themainthread.shipping;

import java.math.BigDecimal;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShippingService {
    private static final BigDecimal FREE = new BigDecimal("0.00");

    private final ShippingConfig config;

    public ShippingService(ShippingConfig config) {
        this.config = config;
    }

    public ShippingQuote quote(QuoteRequest request) {
        BigDecimal shipping;
        if (request.method() == ShippingMethod.EXPRESS) {
            shipping = config.expressFee();
        } else if (request.subtotal().compareTo(config.freeStandardFrom()) >= 0) {
            shipping = FREE;
        } else {
            shipping = config.standardFee();
        }
        return new ShippingQuote(request.subtotal(), request.method(), shipping, "EUR");
    }
}
