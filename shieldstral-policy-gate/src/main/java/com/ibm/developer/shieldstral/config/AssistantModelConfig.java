package com.ibm.developer.shieldstral.config;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "assistant")
public interface AssistantModelConfig {

    @WithName("base-url")
    @WithDefault("https://api.mistral.ai/v1")
    String baseUrl();

    @WithName("api-key")
    @WithDefault("dummy")
    String apiKey();

    @WithName("model-name")
    @WithDefault("mistral-small-latest")
    String modelName();

    @WithDefault("30s")
    Duration timeout();
}
