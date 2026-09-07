package com.themainthread.exoplanets;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/TAP/sync")
@RegisterRestClient(configKey = "nasa")
public interface ArchiveClient {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<Planet> query(@QueryParam("query") String query, @QueryParam("format") String format);
}
