package com.themainthread.checkout;

import java.util.Map;
import java.util.UUID;

import io.quarkus.scheduler.Scheduler;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class DemoResource {

    private final CheckoutConfig config;
    private final OutboxPublisher publisher;
    private final Scheduler scheduler;

    DemoResource(CheckoutConfig config, OutboxPublisher publisher, Scheduler scheduler) {
        this.config = config;
        this.publisher = publisher;
        this.scheduler = scheduler;
    }

    @GET
    @Path("/status")
    public Map<String, Object> status() {
        requireDemo();
        return Map.of("worker", publisher.workerId(), "schedulerRunning", scheduler.isRunning());
    }

    @POST
    @Path("/publish/{id}")
    public Map<String, Object> publish(@PathParam("id") UUID id) {
        requireDemo();
        return Map.of("published", publisher.publishOne(id), "worker", publisher.workerId());
    }

    @POST
    @Path("/publish")
    public Map<String, Object> publish() {
        requireDemo();
        return Map.of("published", publisher.publishPending(), "worker", publisher.workerId());
    }

    @POST
    @Path("/scheduler/{action}")
    public Map<String, Object> scheduler(@PathParam("action") String action) {
        requireDemo();
        switch (action) {
            case "pause" -> scheduler.pause();
            case "resume" -> scheduler.resume();
            default -> throw new BadRequestException("Use pause or resume");
        }
        return status();
    }

    private void requireDemo() {
        if (!config.demo().enabled()) {
            throw new NotFoundException();
        }
    }
}
