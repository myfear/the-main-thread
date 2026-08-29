package com.themainthread.reservation;

import java.util.UUID;

import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkiverse.httpproblem.postprocessing.ProblemContext;
import io.quarkiverse.httpproblem.postprocessing.ProblemPostProcessor;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SupportIdPostProcessor implements ProblemPostProcessor {

    static final int PRIORITY = 50;

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public HttpProblem apply(HttpProblem problem, ProblemContext context) {
        if (problem.getStatusCode() < 500) {
            return problem;
        }
        if (problem.getParameters().containsKey("supportId")) {
            return problem;
        }
        return HttpProblem.builder(problem)
                .with("supportId", UUID.randomUUID().toString())
                .build();
    }
}
