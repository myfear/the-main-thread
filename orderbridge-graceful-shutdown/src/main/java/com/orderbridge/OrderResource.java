package com.orderbridge;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    private final OrderService orderService;

    public OrderResource(OrderService orderService) {
        this.orderService = orderService;
    }

    @GET
    @Path("/{id}")
    public OrderStatus get(@PathParam("id") String orderId) {
        return orderService.status(orderId);
    }

    @POST
    @Path("/handoff")
    public HandoffResult handoff(HandoffRequest request) {
        return orderService.handoff(request);
    }
}
