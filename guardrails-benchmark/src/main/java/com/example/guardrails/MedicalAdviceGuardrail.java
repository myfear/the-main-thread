package com.example.guardrails;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.example.embedding.EmbeddingProvider;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.store.embedding.CosineSimilarity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
public class MedicalAdviceGuardrail implements InputGuardrail {

    private final EmbeddingProvider embeddingProvider;

    private final List<MedicalPattern> medicalPatterns;
    private final double threshold;

    // Constructor for CDI injection (used by ChatBot)
    @Inject
    public MedicalAdviceGuardrail(
            @Named("ollama") EmbeddingProvider embeddingProvider,
            @ConfigProperty(name = "guardrail.medical.threshold") double threshold) {
        this.embeddingProvider = embeddingProvider;
        this.threshold = threshold;
        this.medicalPatterns = initializePatterns(embeddingProvider);
    }

    // Private constructor for programmatic use via factory method
    private MedicalAdviceGuardrail(EmbeddingProvider embeddingProvider, double threshold, boolean unused) {
        this.embeddingProvider = embeddingProvider;
        this.threshold = threshold;
        this.medicalPatterns = initializePatterns(embeddingProvider);
    }

    // Factory method for programmatic use (used by benchmark)
    public static MedicalAdviceGuardrail create(EmbeddingProvider embeddingProvider, double threshold) {
        return new MedicalAdviceGuardrail(embeddingProvider, threshold, false);
    }

    private List<MedicalPattern> initializePatterns(EmbeddingProvider provider) {
        // Define medical advice request patterns
        return List.of(
                new MedicalPattern(
                        "diagnosis_request",
                        "What disease do I have? Diagnose my symptoms and tell me what's wrong",
                        provider.embed("What disease do I have? Diagnose my symptoms and tell me what's wrong")),
                new MedicalPattern(
                        "symptom_diagnosis",
                        "I have these symptoms, what condition do I have?",
                        provider.embed("I have these symptoms, what condition do I have?")),
                new MedicalPattern(
                        "medication_advice",
                        "What medication should I take for this condition?",
                        provider.embed("What medication should I take for this condition?")),
                new MedicalPattern(
                        "treatment_recommendation",
                        "How should I treat this medical condition? What treatment do you recommend?",
                        provider.embed("How should I treat this medical condition? What treatment do you recommend?")),
                new MedicalPattern(
                        "medical_emergency_advice",
                        "I'm having chest pain, what should I do? Is this a medical emergency?",
                        provider.embed("I'm having chest pain, what should I do? Is this a medical emergency?")),
                new MedicalPattern(
                        "prescription_request",
                        "Can you prescribe medication for me? What prescription do I need?",
                        provider.embed("Can you prescribe medication for me? What prescription do I need?")),
                new MedicalPattern(
                        "dosage_advice",
                        "How much of this medication should I take? What's the right dosage?",
                        provider.embed("How much of this medication should I take? What's the right dosage?")),
                new MedicalPattern(
                        "medical_condition_advice",
                        "I have cancer, what should I do? How do I treat this disease?",
                        provider.embed("I have cancer, what should I do? How do I treat this disease?")));
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        Log.debugf("Validating medical advice request for user message: %s", userMessage.singleText());
        String input = userMessage.singleText();
        Embedding inputEmbedding = embeddingProvider.embed(input);

        MedicalMatch bestMatch = medicalPatterns.stream()
                .map(pattern -> new MedicalMatch(
                        pattern.category(),
                        CosineSimilarity.between(inputEmbedding, pattern.embedding())))
                .max((a, b) -> Double.compare(a.similarity(), b.similarity()))
                .orElse(new MedicalMatch("none", 0.0));

        Log.infof("Best medical pattern match: %s (similarity: %.4f, threshold: %.4f)",
                bestMatch.category(), bestMatch.similarity(), threshold);

        if (bestMatch.similarity() >= threshold) {
            Log.warnf("Medical advice request detected: %s (similarity: %.4f >= %.4f)",
                    bestMatch.category(), bestMatch.similarity(), threshold);
            return failure("Medical advice request detected: " + bestMatch.category() + 
                    ". Please consult with a qualified healthcare professional for medical advice.");
        }

        return success();
    }

    private record MedicalPattern(String category, String description, Embedding embedding) {
    }

    private record MedicalMatch(String category, double similarity) {
    }
}

