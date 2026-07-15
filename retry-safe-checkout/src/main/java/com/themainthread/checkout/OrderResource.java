package com.themainthread.checkout;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import io.quarkiverse.idempotency.runtime.Idempotent;
import io.smallrye.common.annotation.Blocking;

@Path("/orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    private final OrderService orderService;

    public OrderResource(OrderService orderService) {
        this.orderService = orderService;
    }

    @POST
    @Blocking
    @Idempotent(requireKey = Idempotent.Require.REQUIRED)
    public Response create(@Valid CheckoutRequest request) {
        OrderView order = orderService.create(request);
        URI location = UriBuilder.fromResource(OrderResource.class)
                .path(OrderResource.class, "get")
                .build(order.id());
        return Response.created(location).entity(order).build();
    }

    @GET
    @Path("/{id}")
    @Blocking
    public OrderView get(@PathParam("id") long id) {
        return orderService.find(id).orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/stats")
    @Blocking
    public CheckoutStats stats() {
        return orderService.stats();
    }
}
