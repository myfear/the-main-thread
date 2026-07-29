package dev.mainthread.delegation.audit;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/audit-events")
@Authenticated
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

    private static final Logger LOG = Logger.getLogger(AuditResource.class);

    private final JsonWebToken accessToken;

    public AuditResource(JsonWebToken accessToken) {
        this.accessToken = accessToken;
    }

    @POST
    public ClaimSnapshot record(
            @HeaderParam("X-Correlation-ID") String correlationId,
            AuditEvent event) {
        ClaimSnapshot snapshot = ClaimSnapshot.from("audit-service", accessToken, correlationId);
        LOG.infof(
                "Audit event correlationId=%s subject=%s client=%s action=%s orderId=%s tokenId=%s",
                snapshot.correlationId(),
                snapshot.subject(),
                snapshot.authorizedParty(),
                event.action(),
                event.orderId(),
                snapshot.tokenId());
        return snapshot;
    }
}
