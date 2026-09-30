package com.themainthread.decisions;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;

@ApplicationScoped
public class DecisionToolGuardrail implements ToolInputGuardrail {
    private final ReviewService reviews;

    DecisionToolGuardrail(ReviewService reviews) {
        this.reviews = reviews;
    }

    @Override
    public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
        Object userRequest = request.invocationContext() == null ? null
                : request.invocationContext().parameter("userRequest");
        if (!(userRequest instanceof String text) || text.isBlank()) {
            return ToolInputGuardrailResult.fatal("Original user request is missing",
                    new IllegalStateException("Supply the original request through InvocationParameters"));
        }
        ReviewRequest proposal;
        try {
            proposal = new ReviewRequest(text, request.toolName(), request.argumentsAsJson().getMap());
        } catch (RuntimeException e) {
            return ToolInputGuardrailResult.fatal("Invalid tool arguments", e);
        }
        ReviewResponse review = reviews.review(proposal);
        if (review.outcome() == ReviewResponse.Outcome.ALLOW) {
            return ToolInputGuardrailResult.success();
        }
        // End this invocation. The agent cannot retry until it happens to obtain a passing score.
        return ToolInputGuardrailResult.fatal(review.reason(), new DecisionGateException(review));
    }

    public static final class DecisionGateException extends RuntimeException {
        private final ReviewResponse review;

        DecisionGateException(ReviewResponse review) {
            super(review.outcome() + ": " + review.reason());
            this.review = review;
        }

        public ReviewResponse review() {
            return review;
        }
    }
}
