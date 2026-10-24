package com.themainthread.shipping;

import java.math.BigDecimal;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "shipping")
public interface ShippingConfig {
    BigDecimal freeStandardFrom();

    BigDecimal standardFee();

    BigDecimal expressFee();
}
