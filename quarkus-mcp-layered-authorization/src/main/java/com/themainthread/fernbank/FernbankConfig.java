package com.themainthread.fernbank;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "fernbank")
public interface FernbankConfig {

    String runtimeEnvironment();
}
