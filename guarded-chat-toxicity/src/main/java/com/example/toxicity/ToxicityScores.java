package com.example.toxicity;

import java.util.Map;

public record ToxicityScores(Map<String, Double> probabilities) {

    public double get(String label) {
        return probabilities.getOrDefault(label, 0.0);
    }

    public String highestLabel() {
        return probabilities.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    public double highestScore() {
        return probabilities.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
    }
}