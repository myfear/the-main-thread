package com.example.guardrails;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.example.toxicity.ToxicityClassifier;
import com.example.toxicity.ToxicityScores;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ToxicityGuardrail implements InputGuardrail {

    private final ToxicityClassifier classifier;

    private final Map<String, Double> thresholds;

    @Inject
    public ToxicityGuardrail(ToxicityClassifier classifier,
            @ConfigProperty(name = "toxicity.threshold.toxicity") double toxicity,
            @ConfigProperty(name = "toxicity.threshold.severe_toxicity") double severeToxicity,
            @ConfigProperty(name = "toxicity.threshold.identity_attack") double identityAttack,
            @ConfigProperty(name = "toxicity.threshold.threat") double threat,
            @ConfigProperty(name = "toxicity.threshold.insult") double insult,
            @ConfigProperty(name = "toxicity.threshold.obscene") double obscene,
            @ConfigProperty(name = "toxicity.threshold.sexual_explicit") double sexualExplicit) {

        this.classifier = classifier;

        this.thresholds = new LinkedHashMap<>();
        thresholds.put("toxicity", toxicity);
        thresholds.put("severe_toxicity", severeToxicity);
        thresholds.put("identity_attack", identityAttack);
        thresholds.put("threat", threat);
        thresholds.put("insult", insult);
        thresholds.put("obscene", obscene);
        thresholds.put("sexual_explicit", sexualExplicit);
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();
        ToxicityScores scores = classifier.predict(input);

        for (var entry : thresholds.entrySet()) {
            String label = entry.getKey();
            double threshold = entry.getValue();
            double score = scores.get(label);

            if (score >= threshold) {
                Log.warnf("Toxicity blocked: label=%s score=%.4f threshold=%.4f text=%s",
                        label, score, threshold, input);

                return failure("Toxicity detected (" + label + "): " + String.format("%.2f", score));
            }
        }

        Log.infof("Toxicity passed: highest=%s score=%.4f",
                scores.highestLabel(), scores.highestScore());

        return success();
    }
}