package dev.themainthread.meridian.resource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Path("/.well-known")
public class ApiCatalogResource {

    private static final String API_CATALOG_TYPE =
            "application/linkset+json; profile=\"https://www.rfc-editor.org/info/rfc9727\"";

    @ConfigProperty(name = "meridian.api.base-url")
    String apiBaseUrl;

    @HEAD
    @Path("/api-catalog")
    public Response apiCatalogHead() {
        return DiscoveryResource.withDiscoveryLinks(Response.noContent(), apiBaseUrl).build();
    }

    @GET
    @Path("/api-catalog")
    @Produces("application/linkset+json")
    public Response apiCatalog() {
        Map<String, Object> catalog = Map.of(
                "linkset",
                List.of(Map.of(
                        "anchor",
                        apiBaseUrl + "/api/v1",
                        "service-desc",
                        List.of(Map.of(
                                "href", apiBaseUrl + "/q/openapi?format=json",
                                "type", "application/json")),
                        "service-doc",
                        List.of(Map.of(
                                "href", "https://docs.meridian.dev",
                                "type", "text/html")),
                        "status",
                        List.of(Map.of(
                                "href", apiBaseUrl + "/q/health",
                                "type", "application/json")))));

        return DiscoveryResource.withDiscoveryLinks(Response.ok(catalog), apiBaseUrl)
                .type(API_CATALOG_TYPE)
                .build();
    }
}
