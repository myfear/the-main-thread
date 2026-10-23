package com.themainthread.poolfaults;

import java.math.BigDecimal;
import java.util.List;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/quotes")
@Produces(MediaType.APPLICATION_JSON)
public class QuoteResource {
    private final QuoteStore store;

    public QuoteResource(QuoteStore store) {
        this.store = store;
    }

    @POST
    public Response save(@QueryParam("destination") @DefaultValue("Berlin") String destination) {
        return Response.status(Response.Status.CREATED).entity(store.save(destination(destination))).build();
    }

    @GET
    public List<QuoteStore.QuoteView> list() {
        return store.list();
    }

    @GET
    @Path("/count")
    public long count() {
        return store.count();
    }

    @GET
    @Path("/preview")
    public Price preview(@QueryParam("destination") @DefaultValue("Berlin") String destination) {
        return new Price(destination(destination), new BigDecimal("9.90"), "EUR", false);
    }

    private static String destination(String destination) {
        if (destination.isBlank() || destination.length() > 100) {
            throw new BadRequestException("Use a destination between 1 and 100 characters.");
        }
        return destination;
    }

    public record Price(String destination, BigDecimal amount, String currency, boolean saved) {
    }
}
