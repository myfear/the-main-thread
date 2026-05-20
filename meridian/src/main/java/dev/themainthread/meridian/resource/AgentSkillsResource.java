package dev.themainthread.meridian.resource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Path("/.well-known/agent-skills")
public class AgentSkillsResource {

    private static final String DISCOVERY_SCHEMA = "https://schemas.agentskills.io/discovery/0.2.0/schema.json";

    private static final String SKILL_MD = """
            ---
            name: meridian
            description: Search and read Meridian knowledge articles. Use when a user asks for Meridian article IDs, public article search, or Markdown article content.
            ---

            # Meridian

            Use the public search endpoint first unless the user already provided an article ID.

            ## Search

            Call:

            ```text
            GET https://api.meridian.dev/api/v1/articles?q={query}
            ```

            Use returned article IDs for follow-up reads.

            ## Read

            Call:

            ```text
            GET https://api.meridian.dev/api/v1/articles/{id}/content
            Accept: text/markdown
            ```

            Prefer Markdown content. Do not scrape HTML unless Markdown is unavailable.
            """;

    @ConfigProperty(name = "meridian.api.base-url")
    String apiBaseUrl;

    @GET
    @Path("/index.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response index() {
        Map<String, Object> index = Map.of(
                "$schema",
                DISCOVERY_SCHEMA,
                "skills",
                List.of(Map.of(
                        "name",
                        "meridian",
                        "type",
                        "skill-md",
                        "description",
                        "Search and read Meridian knowledge articles.",
                        "url",
                        apiBaseUrl + "/.well-known/agent-skills/meridian/SKILL.md",
                        "digest",
                        sha256Digest(SKILL_MD))));

        return Response.ok(index).build();
    }

    @GET
    @Path("/meridian/SKILL.md")
    @Produces("text/markdown")
    public Response skill() {
        return Response.ok(SKILL_MD, "text/markdown")
                .header("Cache-Control", "public, max-age=3600")
                .build();
    }

    private static String sha256Digest(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
