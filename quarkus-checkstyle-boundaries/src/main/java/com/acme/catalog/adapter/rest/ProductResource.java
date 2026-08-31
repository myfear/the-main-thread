package com.acme.catalog.adapter.rest;

import com.acme.catalog.application.CatalogService;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
public class ProductResource {

    private final CatalogService catalogService;

    public ProductResource(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GET
    @Path("/{sku}")
    public ProductResponse getProduct(@PathParam("sku") String sku) {
        return catalogService.findProduct(sku)
                .map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Unknown product: " + sku));
    }
}
