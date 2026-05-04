package dev.topology.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestStreamElementType;

import io.smallrye.mutiny.Multi;

import dev.topology.stream.RunEventPublisher;

@Path("/events")
@ApplicationScoped
public class EventsResource {

    private final RunEventPublisher runEventPublisher;

    @Inject
    public EventsResource(RunEventPublisher runEventPublisher) {
        this.runEventPublisher = runEventPublisher;
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<String> events() {
        return runEventPublisher.stream();
    }
}
