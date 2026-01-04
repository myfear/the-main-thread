package com.vibecheck;

import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.vibecheck.tokenizer.Tokenizer;
import com.vibecheck.tokenizer.TokenizerType;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VibeService {

    private OrtEnvironment env;
    private OrtSession session;

    @jakarta.inject.Inject
    @TokenizerType(TokenizerType.Type.HUGGINGFACE)
    private Tokenizer tokenizer;

    private static final String[] EMOTIONS = {
            "admiration", "amusement", "anger", "annoyance", "approval", "caring",
            "confusion", "curiosity", "desire", "disappointment", "disapproval",
            "disgust", "embarrassment", "excitement", "fear", "gratitude", "grief",
            "joy", "love", "nervousness", "optimism", "pride", "realization", "relief",
            "remorse", "sadness", "surprise", "neutral"
    };

    @PostConstruct
    void init() throws Exception {
        env = OrtEnvironment.getEnvironment();
        session = env.createSession(
                "src/main/resources/model/model.onnx",
                new OrtSession.SessionOptions());
    }

    public VibeResponse checkVibe(String text) throws Exception {
        long[] inputIds = tokenizer.encode(text, 128);
        long[] attention = Arrays.stream(inputIds).map(i -> i == 1 ? 0 : 1).toArray();

        try (
                var idsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), new long[] { 1, 128 });
                var maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attention), new long[] { 1, 128 });
                var results = session.run(Map.of(
                        "input_ids", idsTensor,
                        "attention_mask", maskTensor))) {
            FloatBuffer scores = ((OnnxTensor) results.get(0)).getFloatBuffer();
            return buildResponse(text, scores);
        }
    }

    private VibeResponse buildResponse(String text, FloatBuffer scores) {
        Map<String, Float> all = new HashMap<>();
        float max = Float.NEGATIVE_INFINITY;
        String top = "";

        for (int i = 0; i < EMOTIONS.length; i++) {
            float v = sigmoid(scores.get(i));
            all.put(EMOTIONS[i], v);
            if (v > max) {
                max = v;
                top = EMOTIONS[i];
            }
        }

        return new VibeResponse(text, top, max, all, vibe(top, max));
    }

    private float sigmoid(float x) {
        return (float) (1 / (1 + Math.exp(-x)));
    }

    private String vibe(String emotion, float confidence) {
        if (confidence < 0.5)
            return "🤷 Mixed signals";
        return switch (emotion) {
            case "joy", "amusement", "excitement" -> "✨ Positive vibes";
            case "anger", "annoyance", "disgust" -> "🔥 Not great";
            case "sadness", "grief" -> "😢 Heavy mood";
            case "neutral" -> "😐 Neutral";
            default -> "🎭 Complex emotions";
        };
    }

    @PreDestroy
    void shutdown() throws Exception {
        session.close();
        env.close();
    }
}