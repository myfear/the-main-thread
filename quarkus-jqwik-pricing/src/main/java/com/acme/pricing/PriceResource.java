package com.acme.pricing;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/prices")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PriceResource {

    private final PriceCalculator priceCalculator;

    public PriceResource(PriceCalculator priceCalculator) {
        this.priceCalculator = priceCalculator;
    }

    @POST
    @Path("/calculate")
    public PriceBreakdown calculate(PriceRequest request) {
        return priceCalculator.calculate(request);
    }
}
