package dev.mainthread.incidents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class IncidentVectorizer {

    static final int DIMENSIONS = 384;

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");

    List<Float> vectorForPoint(IncidentInput incident) {
        float[] values = vectorForSearch(incident);
        List<Float> boxed = new ArrayList<>(values.length);
        for (float value : values) {
            boxed.add(value);
        }
        return boxed;
    }

    float[] vectorForSearch(IncidentInput incident) {
        float[] vector = new float[DIMENSIONS];
        addWeighted(vector, "service:" + incident.service(), 5);
        addWeighted(vector, "environment:" + incident.environment(), 2);
        addWeighted(vector, "exception:" + incident.exceptionType(), 6);
        addText(vector, incident.exceptionType(), 3);
        addText(vector, incident.message(), 2);

        for (String frame : incident.stackTrace()) {
            addWeighted(vector, "frame:" + frame, 3);
            addText(vector, frame, 1);
        }

        normalize(vector);
        return vector;
    }

    double cosine(float[] left, float[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private void addText(float[] vector, String text, int weight) {
        if (text == null || text.isBlank()) {
            return;
        }

        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            addWeighted(vector, matcher.group(), weight);
        }
    }

    private void addWeighted(float[] vector, String rawToken, int weight) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String token = rawToken.toLowerCase(Locale.ROOT).trim();
        int bucket = Math.floorMod(token.hashCode(), vector.length);
        vector[bucket] += weight;
    }

    private void normalize(float[] vector) {
        double length = 0;
        for (float value : vector) {
            length += value * value;
        }
        if (length == 0) {
            return;
        }

        float norm = (float) Math.sqrt(length);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }
}
