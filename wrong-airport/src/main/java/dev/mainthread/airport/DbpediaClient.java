// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import java.io.InputStream;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/data")
@RegisterRestClient(configKey = "dbpedia")
@ClientHeaderParam(name = "Accept-Encoding", value = "identity")
public interface DbpediaClient {
    @GET
    @Path("/{resource}.rdf")
    @Produces("application/rdf+xml")
    RestResponse<InputStream> fetch(@PathParam("resource") String resource);
}
