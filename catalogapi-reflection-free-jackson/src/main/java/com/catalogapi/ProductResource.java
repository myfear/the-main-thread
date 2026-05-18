package com.catalogapi;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.catalogapi.json.Page;
import com.catalogapi.json.ProductInput;
import com.catalogapi.json.ProductSummary;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Products")
public class ProductResource {

    @GET
    @Operation(summary = "List all products")
    public List<Product> list() {
        return Product.listAll();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get one product by id")
    public Product get(@PathParam("id") long id) {
        return Product.<Product>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
    }

    @POST
    @Transactional
    @Operation(summary = "Create a product")
    public Response create(ProductInput input) {
        Product product = new Product();
        product.sku = input.sku();
        product.name = input.name();
        product.priceCents = input.priceCents();
        product.category = input.category();
        product.persist();
        return Response.status(Response.Status.CREATED).entity(product).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Update a product")
    public Product update(@PathParam("id") long id, ProductInput input) {
        Product product = Product.<Product>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        product.sku = input.sku();
        product.name = input.name();
        product.priceCents = input.priceCents();
        product.category = input.category();
        return product;
    }

    @GET
    @Path("/summaries")
    @Operation(summary = "List product summaries as records with custom Money serialization")
    public List<ProductSummary> summaries() {
        return Product.<Product>listAll().stream()
                .map(ProductMapper::toSummary)
                .toList();
    }

    @GET
    @Path("/page")
    @Operation(summary = "Paged product summaries in a generic envelope")
    public Page<ProductSummary> page() {
        List<ProductSummary> items = Product.<Product>listAll().stream()
                .map(ProductMapper::toSummary)
                .toList();
        return new Page<>(items, items.size());
    }
}
