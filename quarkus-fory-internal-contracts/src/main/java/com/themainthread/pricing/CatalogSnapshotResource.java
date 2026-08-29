package com.themainthread.pricing;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/catalog/snapshots")
@ApplicationScoped
@Produces(APPLICATION_JSON)
public class CatalogSnapshotResource {

    @GET
    @Path("/sample")
    public PricingSnapshot sample() {
        return SampleSnapshots.sample();
    }
}
