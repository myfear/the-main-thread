package com.example.toxicity;

public interface ToxicityClassifier {
    ToxicityScores predict(String text);

    void warmup();
}