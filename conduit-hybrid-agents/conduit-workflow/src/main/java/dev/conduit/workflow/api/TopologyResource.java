package dev.conduit.workflow.api;

import dev.conduit.workflow.agents.ConduitTopology;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/topology")
@ApplicationScoped
public class TopologyResource {

    private final ConduitTopology conduitTopology;

    @Inject
    public TopologyResource(ConduitTopology conduitTopology) {
        this.conduitTopology = conduitTopology;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String topologyHtml() {
        return HtmlReportGenerator.generateReport(conduitTopology.monitor());
    }
}