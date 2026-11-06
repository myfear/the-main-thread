// Copyright (C) 2026 Markus Eisele
// SPDX-License-Identifier: GPL-3.0-only
package dev.mainthread.airport;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.smallrye.common.annotation.Blocking;

@Path("/airports")
@Produces(MediaType.APPLICATION_JSON)
public class AirportResource {
    private final AirportService service;

    public AirportResource(AirportService service) {
        this.service = service;
    }

    @GET
    @Path("/{code}")
    @Blocking
    public AirportResponse get(@PathParam("code") String code) {
        return service.lookup(code);
    }
}
