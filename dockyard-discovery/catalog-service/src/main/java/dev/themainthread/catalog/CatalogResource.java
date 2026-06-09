package dev.themainthread.catalog;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/catalog")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource {

    private final CatalogInstanceConfig instance;

    public CatalogResource(CatalogInstanceConfig instance) {
        this.instance = instance;
    }

    @GET
    @Path("/{sku}")
    public CatalogResponse get(@PathParam("sku") String sku) {
        return new CatalogResponse(
                sku,
                new BigDecimal("19.99"),
                instance.id(),
                instance.color(),
                Instant.now());
    }
}
