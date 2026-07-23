package com.themainthread.policy;

import com.themainthread.vendor.LegacyDecisionEngine;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/authorization")
@Produces(MediaType.APPLICATION_JSON)
public class AuthorizationResource {

    @GET
    @Path("/{decision}")
    public Response authorize(@PathParam("decision") String decision) {
        boolean allowed = LegacyDecisionEngine.isAllowed(decision);
        DecisionResponse body = new DecisionResponse(decision, allowed);
        Response.Status status = allowed ? Response.Status.OK : Response.Status.FORBIDDEN;
        return Response.status(status).entity(body).build();
    }

    public record DecisionResponse(String decision, boolean allowed) {
    }
}
