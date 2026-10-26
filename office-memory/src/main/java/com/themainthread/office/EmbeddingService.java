package com.themainthread.office;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.ServiceUnavailableException;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class EmbeddingService {
    private final EmbeddingModel model;
    private final OfficeConfig config;

    public EmbeddingService(EmbeddingModel model, OfficeConfig config) {
        this.model = model;
        this.config = config;
    }

    void verify(@Observes StartupEvent event) {
        if (config.verifyEmbeddingOnStart()) {
            generate("search_query: dimension check");
        }
    }

    public Embedding document(String text) {
        return generate("search_document: " + text);
    }

    public Embedding query(String text) {
        return generate("search_query: " + text);
    }

    private Embedding generate(String input) {
        Embedding embedding;
        try {
            embedding = model.embed(input).content();
        } catch (RuntimeException e) {
            throw new ServiceUnavailableException("Embedding model unavailable. Check Ollama and retry.");
        }
        if (embedding.vector().length != 768) {
            throw new IllegalStateException("Office Memory requires 768-dimensional embeddings; received "
                    + embedding.vector().length);
        }
        return embedding;
    }
}
