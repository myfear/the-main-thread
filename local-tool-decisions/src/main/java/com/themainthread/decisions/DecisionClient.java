package com.themainthread.decisions;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import dev.langchain4j.service.decision.DecisionResult;
import dev.langchain4j.service.decision.DecisionServices;

@ApplicationScoped
public class DecisionClient {
    private final ToolDecisions decisions;

    DecisionClient(DecisionConfig config) {
        var model = TypeSafeDecisionModel.builder()
                .baseUrl(config.baseUrl())
                .modelName(config.model())
                .timeout(config.timeout())
                .maxRetries(0)
                .build();
        decisions = DecisionServices.create(ToolDecisions.class, model);
    }

    DecisionResult<ToolDecisions.Assessment> assess(DemoTools.Invocation invocation) {
        try {
            return decisions.assess(invocation.userRequest(), invocation.tool().description,
                    invocation.arguments(), invocation.order().modelInput());
        } catch (RuntimeException e) {
            // The preview adapter also exposes unchecked JSON parsing failures outside LangChain4jException.
            throw new DecisionFailure(e);
        }
    }

    static final class DecisionFailure extends RuntimeException {
        DecisionFailure(RuntimeException cause) {
            super("The decision provider could not supply a valid assessment", cause);
        }
    }
}
