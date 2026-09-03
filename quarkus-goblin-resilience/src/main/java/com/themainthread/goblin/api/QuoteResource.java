package com.themainthread.goblin.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.themainthread.goblin.inventory.InventoryGateway;
import com.themainthread.goblin.inventory.InventorySnapshot;

@Path("/quotes")
@Produces(MediaType.APPLICATION_JSON)
public class QuoteResource {

    private final InventoryGateway inventoryGateway;

    public QuoteResource(InventoryGateway inventoryGateway) {
        this.inventoryGateway = inventoryGateway;
    }

    @GET
    @Path("/{sku}")
    public QuoteResponse quote(@PathParam("sku") String sku) {
        InventorySnapshot inventory = inventoryGateway.inventory(sku);
        String service = inventory.expressEligible() ? "EXPRESS" : "STANDARD";
        return new QuoteResponse(sku, service, inventory.available(), inventory.source());
    }
}
