package com.mainthread.catalog;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/catalog")
@Produces(APPLICATION_JSON)
@ApplicationScoped
public class CatalogResource {

    private static final List<CatalogItem> ITEMS = List.of(
            new CatalogItem("sku-1", "Robot arm"),
            new CatalogItem("sku-2", "Conveyor belt"),
            new CatalogItem("sku-3", "Safety sensor"));

    @GET
    public List<CatalogItem> list() {
        return ITEMS;
    }
}
