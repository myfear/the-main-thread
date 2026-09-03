package com.themainthread.goblin.inventory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/internal/inventory")
@Produces(MediaType.APPLICATION_JSON)
public class InventoryResource {

    @GET
    @Path("/{sku}")
    public InventorySnapshot inventory(@PathParam("sku") String sku) {
        return new InventorySnapshot(sku, 17, true, "live");
    }
}
