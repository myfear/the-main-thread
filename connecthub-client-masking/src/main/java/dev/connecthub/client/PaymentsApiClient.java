package dev.connecthub.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "payments-api")
@RegisterProvider(PaymentsAuthFilter.class)
@Path("/simulate/payments")
public interface PaymentsApiClient {

    @GET
    @Path("/ping")
    @Produces(MediaType.TEXT_PLAIN)
    String ping();
}
