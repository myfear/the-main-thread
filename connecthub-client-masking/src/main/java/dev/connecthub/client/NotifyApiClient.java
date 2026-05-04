package dev.connecthub.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "notify-api")
@RegisterProvider(NotifyAuthFilter.class)
@Path("/simulate/notify")
public interface NotifyApiClient {

    @GET
    @Path("/ping")
    @Produces(MediaType.TEXT_PLAIN)
    String ping();
}
