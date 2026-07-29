package dev.mainthread.delegation.inventory;

import java.util.List;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.Status;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/reservations")
@Authenticated
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ReservationResource {

    private static final Logger LOG = Logger.getLogger(ReservationResource.class);

    private final AuditClient auditClient;
    private final JsonWebToken accessToken;

    public ReservationResource(@RestClient AuditClient auditClient, JsonWebToken accessToken) {
        this.auditClient = auditClient;
        this.accessToken = accessToken;
    }

    @POST
    public ReservationResult reserve(
            @HeaderParam("X-Correlation-ID") String correlationId,
            ReservationRequest request) {
        ClaimSnapshot inventoryHop = ClaimSnapshot.from("inventory-service", accessToken, correlationId);
        logHop(inventoryHop, "service-c");

        try {
            ClaimSnapshot auditHop = auditClient.record(
                    correlationId,
                    new AuditEvent(request.orderId(), "inventory-reserved"));
            return new ReservationResult(
                    request.orderId(),
                    "submitted",
                    List.of(inventoryHop, auditHop));
        } catch (RuntimeException failure) {
            throw new DownstreamFailureException("audit-service", correlationId, failure);
        }
    }

    @ServerExceptionMapper
    RestResponse<ErrorResponse> mapDownstreamFailure(DownstreamFailureException failure) {
        LOG.errorf(
                "Delegation failed correlationId=%s cause=%s",
                failure.correlationId(),
                failure.getCause().getClass().getSimpleName());
        return RestResponse.status(
                Status.BAD_GATEWAY,
                new ErrorResponse(
                        "downstream_unavailable",
                        failure.getMessage(),
                        failure.correlationId()));
    }

    private static void logHop(ClaimSnapshot hop, String targetAudience) {
        LOG.infof(
                "Delegating correlationId=%s subject=%s client=%s targetAudience=%s tokenId=%s",
                hop.correlationId(),
                hop.subject(),
                hop.authorizedParty(),
                targetAudience,
                hop.tokenId());
    }
}
