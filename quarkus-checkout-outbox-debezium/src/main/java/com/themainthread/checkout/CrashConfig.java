package com.themainthread.checkout;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "checkout.crash")
public interface CrashConfig {
    @WithDefault("false")
    boolean enabled();

    @WithDefault("true")
    boolean halt();
}
