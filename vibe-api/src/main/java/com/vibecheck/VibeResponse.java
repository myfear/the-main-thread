package com.vibecheck;

import java.util.Map;

public record VibeResponse(String text, String topEmotion, float confidence, Map<String, Float> allEmotions,
        String vibeCheck) {
}