package com.themainthread.rag;

import java.nio.file.Path;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "rag")
public interface RagConfig {
    @WithDefault("documents")
    Path documentsDirectory();

    @WithDefault("converted")
    Path convertedDirectory();

    @WithDefault("600")
    int chunkCharacters();

    @WithDefault("80")
    int overlapCharacters();

    @WithDefault("384")
    int embeddingDimension();

    @WithDefault("0.6")
    double minimumScore();
}
