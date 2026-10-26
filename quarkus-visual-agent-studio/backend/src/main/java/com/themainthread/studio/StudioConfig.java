package com.themainthread.studio;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "studio")
public interface StudioConfig {
    String internalToken();

    String runnerToken();

    String executorBaseUrl();
}
