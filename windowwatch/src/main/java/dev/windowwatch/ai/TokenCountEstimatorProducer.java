package dev.windowwatch.ai;

import java.nio.file.Path;

import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenCountEstimator;
import dev.windowwatch.config.WindowWatchConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class TokenCountEstimatorProducer {

    private final WindowWatchConfig config;

    @Inject
    TokenCountEstimatorProducer(WindowWatchConfig config) {
        this.config = config;
    }

    @Produces
    TokenCountEstimator tokenCountEstimator() {
        return new HuggingFaceTokenCountEstimator(Path.of(config.tokenizer().path()));
    }
}
