package com.themainthread.shapeshift;

import java.util.Map;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IntentResource {
    private final IntentClassifier classifier;
    private final CardParser parser;
    private final ShapeConfig config;
    private final InputReadiness readiness;

    public record Request(String text, Boolean useCache) {
    }

    public record ParseRequest(String text, Intent intent) {
    }

    public record Result(IntentClassifier.Prediction prediction, CardParser.Card card, long serverMillis, String source,
            boolean ready, String waitingReason) {
    }

    public IntentResource(IntentClassifier classifier, CardParser parser, ShapeConfig config, InputReadiness readiness) {
        this.classifier = classifier;
        this.parser = parser;
        this.config = config;
        this.readiness = readiness;
    }

    @POST
    @Path("/intent")
    public Result classify(Request request) {
        validate(request == null ? null : request.text());
        long started = System.nanoTime();
        String waiting = readiness.waitingReason(request.text());
        if (waiting != null) {
            return new Result(IntentClassifier.Prediction.waiting(config.model()), null,
                    (System.nanoTime() - started) / 1_000_000,
                    request.text().trim().length() < 2 ? "empty" : "input-gate", false, waiting);
        }
        var prediction = classifier.classify(request.text(), !Boolean.FALSE.equals(request.useCache()));
        var card = parse(prediction.intent(), request.text());
        boolean ready = prediction.intent() != Intent.NONE;
        if (prediction.intent() == Intent.TIMER) {
            ready = card.fields().get("seconds") instanceof Number seconds && seconds.longValue() > 0;
        } else if (prediction.intent() == Intent.SPLIT) {
            ready = card.fields().get("people") instanceof Number people && people.intValue() > 0;
        } else if (prediction.intent() == Intent.CHECKLIST) {
            ready = card.fields().get("items") instanceof java.util.List<?> items && items.size() >= 2;
        }
        return new Result(prediction, ready ? card : null, (System.nanoTime() - started) / 1_000_000, "ollama",
                ready, ready ? null : "Add enough detail for this card, or choose it manually.");
    }

    @POST
    @Path("/parse")
    public CardParser.Card parse(ParseRequest request) {
        validate(request == null ? null : request.text());
        if (request.intent() == null || request.intent() == Intent.NONE) {
            throw new BadRequestException("Choose one of the six card types");
        }
        return parse(request.intent(), request.text());
    }

    private CardParser.Card parse(Intent intent, String text) {
        try {
            return parser.parse(intent, text);
        } catch (ArithmeticException | NumberFormatException e) {
            return new CardParser.Card(intent.name(), Map.of("title", text),
                    java.util.List.of("A numeric value is too large. Edit the card."));
        }
    }

    @GET
    @Path("/config")
    public Map<String, Object> configuration() {
        return Map.of("model", config.model(), "zone", config.zone(), "debounceMs", 250,
                "maximumDebounceMs", 1000, "adaptiveDebounce", true, "inputRules", readiness.rules(),
                "minimumProbability", 0.55, "minimumMargin", 0.15, "questionCount", 2);
    }

    private void validate(String text) {
        if (text == null || text.length() > 500) {
            throw new BadRequestException("Expected text with at most 500 characters");
        }
    }
}
