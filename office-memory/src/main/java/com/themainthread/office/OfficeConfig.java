package com.themainthread.office;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "office.memory")
public interface OfficeConfig {
    @WithDefault("true")
    boolean verifyEmbeddingOnStart();

    @WithDefault("0.55")
    double minimumScore();
}
