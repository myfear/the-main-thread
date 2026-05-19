package com.orderdesk;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/products")
public class ProductResource {

    private final ProductCatalog catalog;

    public ProductResource(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    @GET
    public List<ProductDto> listProducts() {
        return catalog.listAll();
    }
}
