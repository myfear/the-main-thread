package dev.themainthread.meridian.resource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Path("/.well-known")
public class McpServerCardResource {

    @ConfigProperty(name = "meridian.api.base-url")
    String apiBaseUrl;

    @GET
    @Path("/mcp/server-card.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response serverCard() {
        return Response.ok(card()).build();
    }

    @GET
    @Path("/mcp.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response legacyServerCard() {
        return Response.ok(card()).build();
    }

    private Map<String, Object> card() {
        return Map.of(
                "$schema",
                "https://static.modelcontextprotocol.io/schemas/mcp-server-card/v1.json",
                "version",
                "1.0",
                "protocolVersion",
                "2025-06-18",
                "serverInfo",
                Map.of(
                        "name", "meridian",
                        "title", "Meridian Knowledge API",
                        "version", "1.0.0"),
                "description",
                "Search and read public Meridian knowledge articles.",
                "transport",
                Map.of(
                        "type", "streamable-http",
                        "endpoint", apiBaseUrl + "/mcp"),
                "authentication",
                Map.of(
                        "required", true,
                        "schemes", List.of("bearer"),
                        "resourceMetadata", apiBaseUrl + "/.well-known/oauth-protected-resource"),
                "tools",
                List.of(
                        Map.of(
                                "name", "searchArticles",
                                "title", "Search articles",
                                "description", "Search public Meridian articles by keyword or phrase.",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "query", Map.of(
                                                        "type", "string",
                                                        "description", "Search keyword or phrase")),
                                        "required", List.of("query"))),
                        Map.of(
                                "name", "getArticle",
                                "title", "Read article",
                                "description", "Read one Meridian article as Markdown.",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "id", Map.of(
                                                        "type", "string",
                                                        "description", "Article ID")),
                                        "required", List.of("id")))));
    }
}
