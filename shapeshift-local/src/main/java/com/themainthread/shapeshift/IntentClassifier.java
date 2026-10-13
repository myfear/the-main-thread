package com.themainthread.shapeshift;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;

import org.jboss.logging.Logger;

import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import dev.langchain4j.service.decision.DecisionServices;

@ApplicationScoped
public class IntentClassifier {
    private static final Logger LOG = Logger.getLogger(IntentClassifier.class);
    private final CardDecisions decisions;
    private final ShapeConfig config;
    private final Semaphore inference = new Semaphore(1);
    private final Map<String, Prediction> cache = new LinkedHashMap<>(128, 0.75f, true);

    public record Prediction(Intent intent, Map<Intent, Double> probabilities, Double confidence,
            double margin, double urgent, String model, long inferenceMillis, boolean cached) {
        static Prediction waiting(String model) {
            // This is a policy result, so it deliberately carries no model probabilities.
            return new Prediction(Intent.NONE, Map.of(), null, 0, 0, model, 0, false);
        }

        Prediction cacheHit() {
            return new Prediction(intent, probabilities, confidence, margin, urgent, model, 0, true);
        }
    }

    public IntentClassifier(ShapeConfig config) {
        this.config = config;
        var model = TypeSafeDecisionModel.builder().baseUrl(config.ollamaUrl()).modelName(config.model())
                .timeout(config.timeout()).maxRetries(0).build();
        decisions = DecisionServices.create(CardDecisions.class, model);
    }

    Prediction classify(String text, boolean useCache) {
        // Cache only model decisions. Date parsing runs again against today's clock.
        if (useCache) {
            synchronized (cache) {
                var hit = cache.get(text);
                if (hit != null) {
                    return hit.cacheHit();
                }
            }
        }
        if (!inference.tryAcquire()) {
            throw new WebApplicationException("Model is busy; retry the latest input", 429);
        }
        try {
            long started = System.nanoTime();
            var result = decisions.classify(text);
            var choice = result.intent();
            var prediction = new Prediction(choice.value(), choice.probabilities(), choice.confidence(),
                    choice.margin(), result.urgent().probability(), config.model(),
                    (System.nanoTime() - started) / 1_000_000, false);
            if (useCache) {
                synchronized (cache) {
                    cache.put(text, prediction);
                    if (cache.size() > 128) {
                        cache.remove(cache.keySet().iterator().next());
                    }
                }
            }
            return prediction;
        } catch (RuntimeException e) {
            LOG.warnf("Local classification failed: %s", e.getClass().getSimpleName());
            throw new WebApplicationException("Ollama classification failed. Check the local server and model.", 503);
        } finally {
            inference.release();
        }
    }
}
