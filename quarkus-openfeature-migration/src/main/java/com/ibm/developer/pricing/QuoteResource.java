package com.ibm.developer.pricing;

import java.math.BigDecimal;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;

import io.smallrye.mutiny.Uni;

@Path("/quotes")
@Produces(MediaType.APPLICATION_JSON)
public class QuoteResource {

    private final PricingService pricingService;

    public QuoteResource(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GET
    @Path("/{tenantId}")
    public Uni<Quote> quote(@RestPath String tenantId, @RestQuery BigDecimal subtotal) {
        if (subtotal == null || subtotal.signum() <= 0) {
            throw new BadRequestException("subtotal must be greater than zero");
        }
        return pricingService.createQuote(tenantId, subtotal);
    }
}
