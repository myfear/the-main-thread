package com.orderdesk;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    private final OrderAssemblyService assemblyService;
    private final String sampleOrderId;

    public OrderResource(
            OrderAssemblyService assemblyService,
            @ConfigProperty(name = "orderdesk.sample-order-id", defaultValue = "ORD-2026-001") String sampleOrderId) {
        this.assemblyService = assemblyService;
        this.sampleOrderId = sampleOrderId;
    }

    @GET
    @Path("/sample")
    public OrderDto sampleOrder() {
        return assemblyService.buildSampleOrder(sampleOrderId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public OrderDto createOrder(@Valid CreateOrderRequest request) {
        return assemblyService.assembleFromRequest(request);
    }
}
