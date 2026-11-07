package com.themainthread.streamhouse.api;

import com.themainthread.streamhouse.projection.ContextQueryService;

import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class OrderContextResource {
    private final ContextQueryService queries;

    public OrderContextResource(ContextQueryService queries) {
        this.queries = queries;
    }

    @GET
    @Path("/{orderId}/context")
    public OrderContextResponse context(@PathParam("orderId") long orderId) {
        return queries.query(orderId);
    }
}
