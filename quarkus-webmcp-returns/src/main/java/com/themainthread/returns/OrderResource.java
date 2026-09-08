package com.themainthread.returns;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {
    private final ReturnService returns;

    public OrderResource(ReturnService returns) {
        this.returns = returns;
    }

    @GET
    @Path("/{orderId}")
    public ReturnService.Order order(@PathParam("orderId") String orderId) {
        return returns.order(orderId);
    }

    @POST
    @Path("/{orderId}/return-preview")
    @Consumes(MediaType.APPLICATION_JSON)
    public ReturnService.Draft preview(@PathParam("orderId") String orderId, ReturnService.ReturnRequest request) {
        return returns.preview(orderId, request);
    }

    @ServerExceptionMapper
    public Response invalid(ReturnService.InvalidReturn error) {
        return Response.status(400).entity(new ErrorMessage(error.getMessage())).build();
    }

    public record ErrorMessage(String error) {
    }
}
