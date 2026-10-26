package com.themainthread.studio;

import java.util.Map;
import java.util.UUID;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.smallrye.common.annotation.Blocking;

@Path("/internal/spike/policy")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class PolicyResource {
    private final StudioConfig config;

    public PolicyResource(StudioConfig config) {
        this.config = config;
    }

    @POST
    public Map<String, Object> evaluate(PolicyInput input, @HeaderParam("X-Studio-Internal-Token") String token) {
        if (!StudioAccessFilter.matches(config.internalToken(), token)) {
            throw new WebApplicationException(401);
        }
        if (input == null || input.runId() == null || input.changedLines() == null
                || input.changedLines() < 0 || input.changedLines() > 10000 || input.fail() == null) {
            throw new BadRequestException("Provide runId, changedLines (0–10000), and fail.");
        }
        if (input.fail()) {
            throw new WebApplicationException(Response.status(503)
                    .entity(Map.of("code", "SPIKE_EXECUTOR_FAILURE")).build());
        }
        return Map.of("runId", input.runId(), "requiresApproval", input.changedLines() >= 100);
    }

    public record PolicyInput(UUID runId, Integer changedLines, Boolean fail) {
    }
}
