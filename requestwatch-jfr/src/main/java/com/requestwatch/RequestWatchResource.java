package com.requestwatch;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/requests")
@Produces(MediaType.APPLICATION_JSON)
public class RequestWatchResource {

    private final PricingService pricingService;
    private final AllocationService allocationService;

    @Inject
    public RequestWatchResource(PricingService pricingService, AllocationService allocationService) {
        this.pricingService = pricingService;
        this.allocationService = allocationService;
    }

    @GET
    @Path("/fast")
    public FastResponse fast() {
        return pricingService.fast();
    }

    @GET
    @Path("/blocking")
    public BlockingResponse blocking() {
        return pricingService.blocking();
    }

    @GET
    @Path("/blocking-fixed")
    public BlockingResponse blockingFixed() {
        return pricingService.blockingFixed();
    }

    @GET
    @Path("/allocating")
    public AllocationResponse allocating() {
        return allocationService.allocating();
    }
}
