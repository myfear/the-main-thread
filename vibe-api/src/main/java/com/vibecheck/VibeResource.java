package com.vibecheck;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/vibe-check")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class VibeResource {

    @Inject
    VibeService service;

    @POST
    public Response check(VibeRequest request) throws Exception {
        return Response.ok(service.checkVibe(request.text())).build();
    }
}
