package com.themainthread.decisions;

import java.util.LinkedHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

@ApplicationScoped
public class ReviewService {
    private static final Logger LOG = Logger.getLogger(ReviewService.class);

    private final DemoTools tools;
    private final DecisionClient client;
    private final DecisionConfig config;

    ReviewService(DemoTools tools, DecisionClient client, DecisionConfig config) {
        this.tools = tools;
        this.client = client;
        this.config = config;
        if (!(config.denyThreshold() >= 0 && config.denyThreshold() < config.allowThreshold()
                && config.allowThreshold() <= 1 && config.minimumIntentMargin() >= 0
                && config.minimumIntentMargin() <= 1 && config.automaticRefundLimitCents() > 0)) {
            throw new IllegalArgumentException("Invalid decision thresholds or refund limit");
        }
    }

    public ReviewResponse review(ReviewRequest request) {
        DemoTools.Invocation invocation;
        try {
            invocation = tools.validate(request);
        } catch (IllegalArgumentException e) {
            return new ReviewResponse(ReviewResponse.Outcome.DENY, e.getMessage(),
                    request == null ? null : request.toolName(), null, null, 0, null);
        }
        return assess(invocation);
    }

    public ExecutionResponse execute(ReviewRequest request) {
        DemoTools.Invocation invocation;
        try {
            invocation = tools.validate(request);
        } catch (IllegalArgumentException e) {
            return new ExecutionResponse(review(request), false, null);
        }
        ReviewResponse review = assess(invocation);
        Object result = review.outcome() == ReviewResponse.Outcome.ALLOW ? tools.execute(invocation) : null;
        return new ExecutionResponse(review, review.outcome() == ReviewResponse.Outcome.ALLOW, result);
    }

    private ReviewResponse assess(DemoTools.Invocation invocation) {
        long started = System.nanoTime();
        try {
            var decision = client.assess(invocation);
            var assessment = decision.content();
            var intent = assessment.intent();
            var probabilities = new LinkedHashMap<String, Double>();
            intent.probabilities().forEach((key, value) -> probabilities.put(key.name(), value));
            if (probabilities.size() != ToolDecisions.Intent.values().length) {
                return response(invocation, ReviewResponse.Outcome.REVIEW_REQUIRED, "MODEL_MISSING_PROBABILITIES",
                        decision.modelName(), started, null);
            }
            double total = 0;
            double maximum = 0;
            for (double probability : probabilities.values()) {
                total += probability;
                maximum = Math.max(maximum, probability);
            }
            if (Math.abs(total - 1.0) > 0.000001
                    || intent.probabilityOf(intent.value()) < maximum - 0.000001) {
                return response(invocation, ReviewResponse.Outcome.REVIEW_REQUIRED, "MODEL_UNAVAILABLE_OR_INVALID",
                        decision.modelName(), started, null);
            }
            var scores = new ReviewResponse.Scores(intent.value().name(), probabilities,
                    intent.margin(), assessment.aligned().probability());
            ReviewResponse.Outcome outcome;
            String reason;
            if (scores.alignment() <= config.denyThreshold()) {
                outcome = ReviewResponse.Outcome.DENY;
                reason = "ACTION_NOT_ALIGNED";
            } else if (scores.alignment() < config.allowThreshold()
                    || intent.value() != invocation.tool().expectedIntent
                    || scores.intentMargin() < config.minimumIntentMargin()) {
                outcome = ReviewResponse.Outcome.REVIEW_REQUIRED;
                reason = "UNCERTAIN_OR_CONFLICTING_ASSESSMENT";
            } else if (invocation.amountCents() > config.automaticRefundLimitCents()) {
                outcome = ReviewResponse.Outcome.REVIEW_REQUIRED;
                reason = "REFUND_EXCEEDS_AUTOMATIC_LIMIT";
            } else {
                outcome = ReviewResponse.Outcome.ALLOW;
                reason = "CHECKS_PASSED";
            }
            return response(invocation, outcome, reason, decision.modelName(), started, scores);
        } catch (DecisionClient.DecisionFailure e) {
            LOG.warnf("Decision assessment failed: %s", e.getCause().getClass().getSimpleName());
            return response(invocation, ReviewResponse.Outcome.REVIEW_REQUIRED, "MODEL_UNAVAILABLE_OR_INVALID",
                    null, started, null);
        }
    }

    private ReviewResponse response(DemoTools.Invocation invocation, ReviewResponse.Outcome outcome, String reason,
            String modelName, long started, ReviewResponse.Scores scores) {
        return new ReviewResponse(outcome, reason, invocation.tool().name(), invocation.tool().sideEffect,
                modelName, (System.nanoTime() - started) / 1_000_000, scores);
    }

    public record ExecutionResponse(ReviewResponse review, boolean executed, Object result) {
    }
}
