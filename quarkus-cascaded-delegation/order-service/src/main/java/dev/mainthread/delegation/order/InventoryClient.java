package dev.mainthread.delegation.order;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkus.oidc.token.propagation.common.AccessToken;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/reservations")
@RegisterRestClient(configKey = "inventory")
@AccessToken
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface InventoryClient {

    @POST
    ReservationResult reserve(
            @HeaderParam("X-Correlation-ID") String correlationId,
            ReservationRequest request);
}
