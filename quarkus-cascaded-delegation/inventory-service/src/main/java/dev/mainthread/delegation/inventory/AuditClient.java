package dev.mainthread.delegation.inventory;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkus.oidc.token.propagation.common.AccessToken;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/audit-events")
@RegisterRestClient(configKey = "audit")
@AccessToken
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AuditClient {

    @POST
    ClaimSnapshot record(
            @HeaderParam("X-Correlation-ID") String correlationId,
            AuditEvent event);
}
