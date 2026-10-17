package com.themainthread.networkfaults;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;

@Path("/carrier/quotes")
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "carrier")
public interface CarrierClient {

    @GET
    CarrierQuote quote(@QueryParam("destination") String destination);

    @ClientExceptionMapper
    static RuntimeException mapFailure(Response response) {
        if (response.getStatus() == 503) {
            return new CarrierHttpException(503);
        }
        return null;
    }
}
