package dev.themainthread.meridian.resource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Path("/")
public class DiscoveryResource {

    @ConfigProperty(name = "meridian.api.base-url")
    String apiBaseUrl;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response index() {
        Map<String, String> body = Map.of(
                "service", "Meridian Knowledge API",
                "articles", apiBaseUrl + "/api/v1/articles",
                "openapi", apiBaseUrl + "/q/openapi?format=json",
                "apiCatalog", apiBaseUrl + "/.well-known/api-catalog",
                "mcpServerCard", apiBaseUrl + "/.well-known/mcp/server-card.json",
                "agentSkills", apiBaseUrl + "/.well-known/agent-skills/index.json");

        return withDiscoveryLinks(Response.ok(body), apiBaseUrl).build();
    }

    static Response.ResponseBuilder withDiscoveryLinks(Response.ResponseBuilder response, String apiBaseUrl) {
        return response
                .header(
                        "Link",
                        "<" + apiBaseUrl + "/.well-known/api-catalog>; rel=\"api-catalog\"; type=\"application/linkset+json\"")
                .header(
                        "Link",
                        "<" + apiBaseUrl + "/q/openapi?format=json>; rel=\"service-desc\"; type=\"application/json\"")
                .header(
                        "Link",
                        "<" + apiBaseUrl + "/llms.txt>; rel=\"describedby\"; type=\"text/plain\"")
                .header(
                        "Link",
                        "<" + apiBaseUrl
                                + "/.well-known/mcp/server-card.json>; rel=\"mcp-server-card\"; type=\"application/json\"")
                .header(
                        "Link",
                        "<" + apiBaseUrl
                                + "/.well-known/agent-skills/index.json>; rel=\"agent-skills\"; type=\"application/json\"");
    }
}
