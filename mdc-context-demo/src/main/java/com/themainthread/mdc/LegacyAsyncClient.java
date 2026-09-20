package com.themainthread.mdc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LegacyAsyncClient {

    public CompletionStage<ContextObservation> call() {
        return CompletableFuture.supplyAsync(() -> ContextObservation.observe("legacy-library"));
    }
}
