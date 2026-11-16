package com.themainthread.atem;

import java.net.URI;
import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "atem")
public interface RecorderConfig {
    @WithDefault("http://127.0.0.1:9997")
    URI controlUrl();

    @WithDefault("http://127.0.0.1:9996")
    URI playbackUrl();

    @WithDefault("live/atem")
    String streamPath();

    @WithDefault("2s")
    Duration connectTimeout();

    @WithDefault("4s")
    Duration metadataTimeout();

    @WithDefault("2m")
    Duration downloadHeaderTimeout();
}
