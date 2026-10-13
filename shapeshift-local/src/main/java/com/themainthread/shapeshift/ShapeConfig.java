package com.themainthread.shapeshift;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "shape")
public interface ShapeConfig {
    @WithDefault("http://127.0.0.1:11436")
    String ollamaUrl();

    @WithDefault("tev1:4b")
    String model();

    @WithDefault("10s")
    Duration timeout();

    @WithDefault("Europe/Berlin")
    String zone();
}
