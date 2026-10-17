package com.themainthread.networkfaults;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/quotes")
@Produces(MediaType.APPLICATION_JSON)
public class QuoteResource {

    private final QuoteService service;

    public QuoteResource(QuoteService service) {
        this.service = service;
    }

    @GET
    public Response quote(@QueryParam("destination") @DefaultValue("Berlin") String destination) {
        return response(destination, service.retrying(destination));
    }

    @GET
    @Path("/once")
    public Response once(@QueryParam("destination") @DefaultValue("Berlin") String destination) {
        return response(destination, service.once(destination));
    }

    private static Response response(String destination, QuoteResult result) {
        if (result.available()) {
            return Response.ok(result.quote()).build();
        }
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new QuoteError(destination, "carrier_unavailable", result.failure()))
                .build();
    }
}
