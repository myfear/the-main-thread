package dev.topology.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.topology.agents.TriageTopology;

@Path("/topology")
@ApplicationScoped
@Produces(MediaType.TEXT_HTML)
public class TopologyResource {

    private final TriageTopology triageTopology;

    @Inject
    public TopologyResource(TriageTopology triageTopology) {
        this.triageTopology = triageTopology;
    }

    @GET
    public String topologyHtml() {
        return HtmlReportGenerator.generateReport(triageTopology.monitor());
    }
}
