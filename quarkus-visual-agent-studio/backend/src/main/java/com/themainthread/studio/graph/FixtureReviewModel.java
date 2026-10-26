package com.themainthread.studio.graph;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FixtureReviewModel implements ReviewModel {
    @Override
    public ReviewResult review(PullRequest request, GraphCompiler.AgentConfiguration configuration, FileReader files) {
        List<ChangedFile> changes = configuration.allowedToolIds().isEmpty() ? request.files() : files.read();
        for (ChangedFile file : changes) {
            if (file.diff().contains("permitAll(")) {
                return new ReviewResult("Deterministic fixture review", List.of(new Finding(file.path(), 12,
                        Severity.HIGH, "SECURITY", "The fixture opens an endpoint without an authorization check.")));
            }
        }
        return new ReviewResult("Deterministic fixture review", List.of(new Finding(changes.getFirst().path(), 4,
                Severity.LOW, "MAINTAINABILITY", "The fixture changes documentation only.")));
    }
}
