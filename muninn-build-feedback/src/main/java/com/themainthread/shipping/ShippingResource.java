package com.themainthread.shipping;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/shipping-quotes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ShippingResource {
    private final ShippingService shipping;

    public ShippingResource(ShippingService shipping) {
        this.shipping = shipping;
    }

    @POST
    public ShippingQuote quote(@Valid QuoteRequest request) {
        return shipping.quote(request);
    }
}
