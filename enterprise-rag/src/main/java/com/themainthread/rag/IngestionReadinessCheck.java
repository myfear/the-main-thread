package com.themainthread.rag;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class IngestionReadinessCheck implements HealthCheck {
    private final IndexingState state;

    public IngestionReadinessCheck(IndexingState state) {
        this.state = state;
    }

    @Override
    public HealthCheckResponse call() {
        var snapshot = state.snapshot();
        return HealthCheckResponse.named("required-pdf-corpus").status(state.ready())
                .withData("state", snapshot.status().name())
                .withData("documents", snapshot.documents()).withData("chunks", snapshot.chunks()).build();
    }
}
