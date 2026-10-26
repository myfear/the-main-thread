package com.themainthread.studio;

import java.io.IOException;
import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.smallrye.common.annotation.Blocking;

@Path("/api/spike")
@StudioAccess
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class SpikeResource {
    private final SpikePublisher publisher;
    private final WorkflowApplication application;
    private final EventJournal journal;

    public SpikeResource(SpikePublisher publisher, WorkflowApplication application, EventJournal journal) {
        this.publisher = publisher;
        this.application = application;
        this.journal = journal;
    }

    @POST
    @Path("/workflows")
    public Response publish(PublishRequest request) throws IOException {
        return Response.accepted(publisher.publish(request == null ? null : request.version())).build();
    }

    @GET
    @Path("/workflows/{version}")
    public Response publication(@PathParam("version") String version) {
        boolean ready = application.workflowDefinitions().containsKey(new WorkflowDefinitionId("studio", "risk-triage", version));
        return Response.status(ready ? 200 : 404).entity(Map.of("version", version, "status", ready ? "READY" : "UNKNOWN"))
                .build();
    }

    @GET
    @Path("/instances/{instanceId}")
    public Map<String, Object> instance(@PathParam("instanceId") String instanceId) throws Exception {
        return Map.of("instanceId", instanceId, "events", journal.events(instanceId),
                "engineCheckpointRows", journal.checkpoints(instanceId), "lostEvents", journal.lostEvents());
    }

    public record PublishRequest(String version) {
    }
}
