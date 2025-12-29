package com.themainthread;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    @GET
    @Operation(summary = "List all orders", description = "Returns all known orders")
    @APIResponse(responseCode = "200", description = "Successful response", content = @Content(schema = @Schema(implementation = Order.class)))
    public List<Order> list() {
        return List.of(
                new Order("A-123", 42),
                new Order("B-456", 7));
    }
}