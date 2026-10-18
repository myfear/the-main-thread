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

@Path("/api/gifts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GiftResource {
    private final GiftService service;

    public GiftResource(GiftService service) {
        this.service = service;
    }

    @POST
    public Response create(@NotNull @Valid ApiTypes.NewGift request) {
        ApiTypes.GiftView gift = service.create(request.name());
        return Response.created(URI.create("/api/gifts/" + gift.id())).entity(gift).build();
    }

    @GET
    @Path("/{id}")
    public ApiTypes.GiftView get(@PathParam("id") UUID id) {
        return service.get(id);
    }

    @POST
    @Path("/{id}/reservations")
    public ApiTypes.GiftView reserve(@PathParam("id") UUID id, @NotNull @Valid ApiTypes.Reservation request) {
        return service.reserve(id, request.buyer(), false);
    }
}
