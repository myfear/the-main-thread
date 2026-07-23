package com.ibm.developer.pricing;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "pricing.flagd")
public interface FlagdConfig {

    boolean enabled();

    String host();

    int port();
}
