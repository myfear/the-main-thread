package dev.novadeck.api;

import java.util.List;

import dev.novadeck.trace.ToolSearchTraceRegistry;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/trace")
@Produces(MediaType.APPLICATION_JSON)
public class TraceResource {

    @Inject
    ToolSearchTraceRegistry registry;

    @GET
    @Path("/recent")
    public List<ToolSearchTraceRegistry.ToolSearchTraceEntry> recent() {
        return registry.recentEntries();
    }
}
