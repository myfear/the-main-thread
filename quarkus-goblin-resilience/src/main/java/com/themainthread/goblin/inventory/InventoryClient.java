package com.themainthread.goblin.inventory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/internal/inventory")
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "inventory")
public interface InventoryClient {

    @GET
    @Path("/{sku}")
    InventorySnapshot inventory(@PathParam("sku") String sku);
}
