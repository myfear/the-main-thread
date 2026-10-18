package com.themainthread.images;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "images")
public interface ImageConfig {
    @WithDefault("flux3-image-klein")
    String model();

    @WithDefault("data/images")
    Path outputDirectory();

    @WithDefault("mflux-generate-flux3")
    List<String> command();

    @WithDefault("5m")
    Duration timeout();
}
