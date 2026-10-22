package com.themainthread.checkout;

import java.time.Duration;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "checkout")
public interface CheckoutConfig {
    Outbox outbox();
    Demo demo();
    Crash crash();

    interface Outbox {
        @WithDefault("1")
        int batchSize();
        @WithDefault("30s")
        Duration leaseDuration();
        @WithDefault("1s")
        Duration pollEvery();
        @WithDefault("worker")
        String workerLabel();
    }

    interface Demo {
        @WithDefault("false")
        boolean enabled();
    }

    interface Crash {
        @WithDefault("true")
        boolean halt();
    }
}
