package dev.themainthread.checkout;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/catalog")
@RegisterRestClient(baseUri = "stork://catalog-service")
public interface CatalogClient {

    @GET
    @Path("/{sku}")
    @Produces(MediaType.APPLICATION_JSON)
    CatalogQuote get(@PathParam("sku") String sku);
}
