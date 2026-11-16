package com.example;

import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/demo")
@Produces(MediaType.APPLICATION_JSON)
public class ThreadResource {
    private final BatchService batches;

    public ThreadResource(BatchService batches) {
        this.batches = batches;
    }

    @GET
    @Path("/event-loop")
    @NonBlocking
    public Uni<ThreadInfo> eventLoop() {
        return Uni.createFrom().item(info());
    }

    @GET
    @Path("/worker")
    public ThreadInfo worker() {
        return info();
    }

    @GET
    @Path("/hello")
    @RunOnVirtualThread
    public ThreadInfo hello() {
        return info();
    }

    @GET
    @Path("/query")
    @RunOnVirtualThread
    public ThreadInfo query() throws InterruptedException {
        Thread.sleep(500);
        return info();
    }

    @GET
    @Path("/parallel/{count}")
    @RunOnVirtualThread
    public BatchService.Batch parallel(@PathParam("count") int count,
            @QueryParam("delayMs") @DefaultValue("500") int delayMs,
            @QueryParam("timeoutMs") @DefaultValue("2000") int timeoutMs) {
        return batches.run(count, delayMs, timeoutMs);
    }

    private ThreadInfo info() {
        Thread thread = Thread.currentThread();
        return new ThreadInfo(thread.getName(), thread.isVirtual());
    }

    public record ThreadInfo(String name, boolean virtual) {
    }
}
