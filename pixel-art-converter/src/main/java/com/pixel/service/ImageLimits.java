package com.pixel.service;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "pixel")
public interface ImageLimits {
    @WithDefault("2")
    int maxConcurrent();

    @WithDefault("4000000")
    long maxPixels();

    @WithDefault("128")
    int maxBlockSize();
}
