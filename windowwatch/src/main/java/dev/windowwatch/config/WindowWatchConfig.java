package dev.windowwatch.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "windowwatch")
public interface WindowWatchConfig {

    Budget budget();

    Tokenizer tokenizer();

    interface Budget {

        @WithDefault("1200")
        int maxTokens();

        @WithDefault("262144")
        int modelContextTokens();
    }

    interface Tokenizer {

        @WithDefault("tokenizers/qwen3-tokenizer.json")
        String path();
    }
}
