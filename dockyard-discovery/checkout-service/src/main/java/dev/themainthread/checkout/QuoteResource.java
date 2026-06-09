package dev.themainthread.checkout;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/quote")
@Produces(MediaType.APPLICATION_JSON)
public class QuoteResource {

    private final CatalogClient catalogClient;

    public QuoteResource(@RestClient CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @GET
    @Path("/{sku}")
    public CatalogQuote quote(@PathParam("sku") String sku) {
        return catalogClient.get(sku);
    }
}
