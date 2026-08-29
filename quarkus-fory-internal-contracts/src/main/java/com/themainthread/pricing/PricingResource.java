package com.themainthread.pricing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/internal/pricing")
@ApplicationScoped
@Consumes("application/fory")
@Produces("application/fory")
public class PricingResource {

    private final QuoteCalculator quoteCalculator;

    @Inject
    public PricingResource(QuoteCalculator quoteCalculator) {
        this.quoteCalculator = quoteCalculator;
    }

    @POST
    @Path("/quote")
    public QuoteDecision quote(PricingSnapshot snapshot) {
        return quoteCalculator.quote(snapshot);
    }
}
