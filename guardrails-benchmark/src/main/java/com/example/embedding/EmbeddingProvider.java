package com.example.embedding;

import dev.langchain4j.data.embedding.Embedding;

public interface EmbeddingProvider {
    Embedding embed(String text);

    void warmup();
}