package com.themainthread.gifts;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "gift.demo")
public interface DemoConfig {
    @WithDefault("false")
    boolean enabled();

    @WithDefault("10s")
    Duration waitTimeout();
}
