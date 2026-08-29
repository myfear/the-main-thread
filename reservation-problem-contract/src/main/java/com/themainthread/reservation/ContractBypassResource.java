package com.themainthread.reservation;

import java.util.Map;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@Path("/demo")
public class ContractBypassResource {

    @POST
    @Path("/entity-bypass")
    public void entityBypass() {
        throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("message", "This request is bad"))
                        .build());
    }

    @POST
    @Path("/status-bypass")
    public void statusBypass() {
        throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }
}
