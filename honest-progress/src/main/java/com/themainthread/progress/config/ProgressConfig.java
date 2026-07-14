package com.themainthread.progress.config;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "progress")
public interface ProgressConfig {

    @WithDefault("target/staged-uploads")
    String stagingDirectory();

    @WithDefault("500ms")
    Duration streamInterval();

    @WithDefault("150ms")
    Duration processingDelay();

    @WithDefault("5")
    int batchSize();

    @WithDefault("1s")
    String schedulerInterval();
}
