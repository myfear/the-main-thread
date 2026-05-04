package dev.topology.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import dev.topology.agents.TriageTopology;
import dev.topology.stream.RunEventPublisher;

@Path("/runs")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RunResource {

    private static final int SNIPPET_MAX = 200;

    private final TriageTopology triageTopology;
    private final RunEventPublisher runEventPublisher;

    @Inject
    public RunResource(TriageTopology triageTopology, RunEventPublisher runEventPublisher) {
        this.triageTopology = triageTopology;
        this.runEventPublisher = runEventPublisher;
    }

    @POST
    public RunResponse run(RunRequest request) {
        if (request == null || request.request() == null || request.request().isBlank()) {
            throw new jakarta.ws.rs.WebApplicationException(
                    jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.BAD_REQUEST)
                            .entity("{\"error\":\"request must be non-blank\"}")
                            .build());
        }
        String summary = triageTopology.run(request.request());
        String snippet = snippet(request.request());
        runEventPublisher.publishSummary(snippet, summary);
        return new RunResponse(summary);
    }

    private static String snippet(String request) {
        String t = request.trim();
        if (t.length() <= SNIPPET_MAX) {
            return t;
        }
        return t.substring(0, SNIPPET_MAX) + "…";
    }
}
