package com.themainthread.decisions;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "decisions")
public interface DecisionConfig {
    String baseUrl();

    @WithDefault("tev1:4b")
    String model();

    @WithDefault("60s")
    Duration timeout();

    @WithDefault("0.90")
    double allowThreshold();

    @WithDefault("0.10")
    double denyThreshold();

    @WithDefault("0.20")
    double minimumIntentMargin();

    @WithDefault("10000")
    long automaticRefundLimitCents();
}
