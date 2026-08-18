package com.ibm.developer.shieldstral.api;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.ibm.developer.shieldstral.assistant.BoundaryAssistant;
import com.ibm.developer.shieldstral.policy.PolicySurface;

@Path("/assistant")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class AssistantResource {

    private final BoundaryAssistant assistant;

    public AssistantResource(BoundaryAssistant assistant) {
        this.assistant = assistant;
    }

    @POST
    @Path("/{policy}")
    public AssistantResponse ask(@PathParam("policy") String policyPath, AssistantRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BadRequestException("message must not be blank");
        }

        PolicySurface surface = PolicySurface.fromPath(policyPath);
        String answer = switch (surface) {
            case PUBLIC_SUPPORT -> assistant.publicSupport(request.message());
            case SECURITY_RESEARCH -> assistant.securityResearch(request.message());
        };
        return new AssistantResponse(surface.path(), answer);
    }
}
