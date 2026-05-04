package dev.cleardesk.wellknown;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import dev.cleardesk.catalog.SkillCatalogService;

/**
 * App-shaped JSON view of the same skill catalogue Quarkus loads for tool-mode Skills (not a full Agent Skills
 * registry implementation — enough to show the contract as an HTTP surface).
 */
@Path("/.well-known/agent-skills")
public class AgentSkillsResource {

    @Inject
    SkillCatalogService skillCatalogService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public AgentSkillsDocument list() {
        return new AgentSkillsDocument("cleardesk-v1", skillCatalogService.summaries());
    }
}
