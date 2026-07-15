package com.themainthread.checkout;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "checkout")
public interface CheckoutConfig {

    @WithDefault("750ms")
    Duration processingDelay();
}
