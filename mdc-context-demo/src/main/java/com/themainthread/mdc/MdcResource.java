package com.themainthread.mdc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

@Path("/mdc")
@Produces(MediaType.APPLICATION_JSON)
public class MdcResource {

    private final ManagedExecutor managedExecutor;
    private final ThreadContext threadContext;
    private final LegacyAsyncClient legacyAsyncClient;

    public MdcResource(ManagedExecutor managedExecutor, ThreadContext threadContext,
            LegacyAsyncClient legacyAsyncClient) {
        this.managedExecutor = managedExecutor;
        this.threadContext = threadContext;
        this.legacyAsyncClient = legacyAsyncClient;
    }

    @GET
    @Path("/common-pool")
    public CompletionStage<ContextObservation> commonPool() {
        ContextObservation.observe("before-common-pool");
        return CompletableFuture.supplyAsync(() -> ContextObservation.observe("common-pool"));
    }

    @GET
    @Path("/managed")
    public CompletionStage<ContextObservation> managed() {
        ContextObservation.observe("before-managed");
        return managedExecutor.supplyAsync(() -> ContextObservation.observe("managed"));
    }

    @GET
    @Path("/mutiny")
    public Uni<ContextObservation> mutiny() {
        ContextObservation.observe("before-mutiny");
        return Uni.createFrom().item("continue")
                .emitOn(Infrastructure.getDefaultExecutor())
                .map(ignored -> ContextObservation.observe("mutiny"));
    }

    @GET
    @Path("/uncaptured")
    public CompletionStage<CaptureResult> uncaptured() {
        ContextObservation.observe("before-uncaptured");
        return legacyAsyncClient.call()
                .thenApplyAsync(library -> new CaptureResult(library,
                        ContextObservation.observe("uncaptured-continuation")), ForkJoinPool.commonPool());
    }

    @GET
    @Path("/captured")
    public CompletionStage<CaptureResult> captured() {
        ContextObservation.observe("before-captured");
        CompletionStage<ContextObservation> stage = legacyAsyncClient.call();
        return threadContext.withContextCapture(stage)
                .thenApplyAsync(library -> new CaptureResult(library,
                        ContextObservation.observe("captured-continuation")), ForkJoinPool.commonPool());
    }

    @GET
    @Path("/bridged")
    public CompletionStage<CaptureResult> bridged() {
        ContextObservation.observe("before-bridged");
        Context requestContext = Vertx.currentContext();
        Executor returnToRequest = task -> requestContext.runOnContext(ignored -> task.run());
        CompletionStage<ContextObservation> stage = legacyAsyncClient.call();
        return threadContext.withContextCapture(stage)
                .thenApplyAsync(library -> new CaptureResult(library,
                        ContextObservation.observe("bridged-continuation")), returnToRequest);
    }

    @GET
    @Path("/managed-failure")
    public CompletionStage<ContextObservation> managedFailure() {
        return managedExecutor.supplyAsync(() -> {
            ContextObservation failure = ContextObservation.observe("managed-failure");
            throw new WebApplicationException(Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(failure).type(MediaType.APPLICATION_JSON).build());
        });
    }
}
