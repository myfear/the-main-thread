package com.example.embedding;

import java.nio.file.Paths;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

@ApplicationScoped
@Named("onnx")
public class OnnxEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingModel model;

    public OnnxEmbeddingProvider(
            @ConfigProperty(name = "onnx.model.path") String modelPath,
            @ConfigProperty(name = "onnx.tokenizer.path") String tokenizerPath) {
        // We load the model from the local file system.
        // PoolingMode.MEAN is the standard for BERT-based embedding models like Nomic.
        this.model = new OnnxEmbeddingModel(
                Paths.get(modelPath).toAbsolutePath().toString(),
                Paths.get(tokenizerPath).toAbsolutePath().toString(),
                PoolingMode.MEAN);
        Log.infof("ONNX embedding model initialized with model path: %s and tokenizer path: %s", modelPath,
                tokenizerPath);
    }

    @Override
    public Embedding embed(String text) {
        // No manual tokenization needed! LangChain4j handles it.
        return model.embed(text).content();
    }

    @Override
    public void warmup() {
        embed("warmup");
    }
}