package dev.conduit.workflow.api;

import java.util.HashMap;
import java.util.Map;

import dev.conduit.workflow.agents.ConduitTopology;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/runs")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RunResource {

    private final ConduitTopology conduitTopology;

    @Inject
    public RunResource(ConduitTopology conduitTopology) {
        this.conduitTopology = conduitTopology;
    }

    @POST
    public RunResponse run(RunRequest request) {
        if (request == null || request.rawId() == null || request.rawId().isBlank()) {
            throw new jakarta.ws.rs.WebApplicationException(
                    jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.BAD_REQUEST)
                            .entity("{\"error\":\"rawId must be non-blank\"}")
                            .build());
        }
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("rawId", request.rawId().trim());
        inputs.put("payload_snippet", request.payloadSnippet() == null ? "" : request.payloadSnippet());

        String queue = conduitTopology.run(inputs);
        return new RunResponse(queue);
    }
}