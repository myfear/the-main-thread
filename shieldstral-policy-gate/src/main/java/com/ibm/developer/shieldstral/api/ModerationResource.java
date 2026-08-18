package com.ibm.developer.shieldstral.api;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.ibm.developer.shieldstral.policy.PolicyDirection;
import com.ibm.developer.shieldstral.policy.PolicyGate;
import com.ibm.developer.shieldstral.policy.PolicySurface;
import com.ibm.developer.shieldstral.policy.SafetyAssessment;

@Path("/moderation")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class ModerationResource {

    private final PolicyGate gate;

    public ModerationResource(PolicyGate gate) {
        this.gate = gate;
    }

    @POST
    @Path("/{policy}/{direction}")
    public SafetyAssessment moderate(
            @PathParam("policy") String policyPath,
            @PathParam("direction") String directionPath,
            ModerationRequest request) {
        if (request == null || request.document() == null || request.document().isBlank()) {
            throw new BadRequestException("document must not be blank");
        }

        return gate.evaluate(
                PolicySurface.fromPath(policyPath),
                PolicyDirection.fromPath(directionPath),
                request.document());
    }
}
