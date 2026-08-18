package com.ibm.developer.shieldstral.config;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "shieldstral")
public interface ShieldstralConfig {

    @WithName("base-url")
    @WithDefault("http://localhost:8000/v1")
    String baseUrl();

    @WithName("api-key")
    @WithDefault("unused")
    String apiKey();

    @WithName("model-name")
    @WithDefault("mistralai/Shieldstral-1.0-3B")
    String modelName();

    @WithDefault("60s")
    Duration timeout();
}
