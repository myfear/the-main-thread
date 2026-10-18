package dev.mainthread.openui;

import java.util.Map;
import java.util.UUID;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;

import io.smallrye.mutiny.Multi;

@Path("/api/generate")
@Consumes(MediaType.APPLICATION_JSON)
public class GenerationResource {
    public record Input(String runId, String notes) { }

    private final GenerationService generations;

    GenerationResource(GenerationService generations) {
        this.generations = generations;
    }

    @POST
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<OutboundSseEvent> generate(Input input, @Context Sse sse) {
        if (input == null || input.notes() == null || input.notes().isBlank() || input.notes().length() > 12_000) {
            throw new WebApplicationException("Supply between 1 and 12000 characters of activity notes", 400);
        }
        try {
            UUID.fromString(input.runId());
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new WebApplicationException("Supply a UUID runId", 400);
        }
        return generations.generate(input.runId(), input.notes()).map(event -> sse.newEventBuilder()
                .mediaType(MediaType.APPLICATION_JSON_TYPE).data(Map.class, event).build());
    }

    @DELETE
    @Path("/{runId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> cancel(@PathParam("runId") String runId) {
        return Map.of("cancelled", generations.cancel(runId));
    }
}
