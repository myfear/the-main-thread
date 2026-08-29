package com.themainthread.reservation;

import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/reservations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ReservationResource {

    private final InventoryService inventory;

    public ReservationResource(InventoryService inventory) {
        this.inventory = inventory;
    }

    @POST
    @APIResponse(responseCode = "409", description = "The requested quantity is no longer available")
    public Response reserve(@Valid ReservationRequest request) throws NotFoundException {
        Reservation reservation = inventory.reserve(request.sku(), request.quantity());
        return Response.status(Response.Status.CREATED).entity(reservation).build();
    }
}
