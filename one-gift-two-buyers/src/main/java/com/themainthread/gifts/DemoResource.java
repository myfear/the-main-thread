package com.themainthread.gifts;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/demo/rounds")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DemoResource {
    private final DemoService demo;
    private final GiftService gifts;
    private final RaceGate gate;

    public DemoResource(DemoService demo, GiftService gifts, RaceGate gate) {
        this.demo = demo;
        this.gifts = gifts;
        this.gate = gate;
    }

    @POST
    public Response create() {
        ApiTypes.Round round = demo.create();
        return Response.created(URI.create("/api/demo/rounds/" + round.id())).entity(round).build();
    }

    @GET
    @Path("/{id}")
    public ApiTypes.Round get(@PathParam("id") UUID id) {
        return demo.get(id);
    }

    @POST
    @Path("/{id}/{mode}/reservations")
    public ApiTypes.GiftView reserve(@PathParam("id") UUID id, @PathParam("mode") String mode,
            @NotNull @Valid ApiTypes.Reservation request) {
        gate.requireEnabled();
        return switch (mode) {
            case "unsafe" -> demo.reserveUnsafe(id, request.buyer());
            case "optimistic" -> gifts.reserve(id, request.buyer(), true);
            default -> throw new ApiFailure(404, "mode_not_found", "Choose unsafe or optimistic.", id);
        };
    }
}
