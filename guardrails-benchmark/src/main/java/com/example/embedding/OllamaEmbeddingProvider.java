package com.example.embedding;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@ApplicationScoped
@Named("ollama")
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingModel model;

    public OllamaEmbeddingProvider(
            @ConfigProperty(name = "quarkus.langchain4j.ollama.base-url") String baseUrl,
            @ConfigProperty(name = "quarkus.langchain4j.ollama.embedding-model.model-id") String modelId) {
        this.model = OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelId)
                .build();
        Log.infof("Ollama embedding model initialized with base URL: %s and model ID: %s", baseUrl, modelId);
    }

    @Override
    public Embedding embed(String text) {
        return model.embed(text).content();
    }

    @Override
    public void warmup() {
        embed("warmup");
    }
}