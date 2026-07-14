package com.themainthread.swiftship;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/shipments")
@Produces(MediaType.APPLICATION_JSON)
public class ShipmentResource {

    private final ShipmentService shipmentService;

    public ShipmentResource(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GET
    public List<ShipmentView> list() {
        return shipmentService.list();
    }

    @GET
    @Path("/summary")
    public ShipmentSummary summary() {
        return shipmentService.summary();
    }

    @GET
    @Path("/{trackingNumber}")
    public ShipmentView find(@PathParam("trackingNumber") String trackingNumber) {
        return shipmentService.find(trackingNumber)
                .orElseThrow(() -> new NotFoundException("Unknown tracking number: " + trackingNumber));
    }
}
