package com.mainthread.shipping;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/shipping/quote")
@Produces(APPLICATION_JSON)
@ApplicationScoped
public class ShippingQuoteResource {

    @GET
    public ShippingQuote quote() {
        return new ShippingQuote("standard", 12);
    }
}
