package dev.mainthread.delegation.order;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.Status;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/orders")
@Authenticated
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    private static final Logger LOG = Logger.getLogger(OrderResource.class);

    private final InventoryClient inventoryClient;
    private final JsonWebToken accessToken;

    public OrderResource(@RestClient InventoryClient inventoryClient, JsonWebToken accessToken) {
        this.inventoryClient = inventoryClient;
        this.accessToken = accessToken;
    }

    @POST
    @Path("/{orderId}/submit")
    public DelegationTrace submit(
            @PathParam("orderId") String orderId,
            @HeaderParam("X-Correlation-ID") String incomingCorrelationId) {
        String correlationId = incomingCorrelationId == null || incomingCorrelationId.isBlank()
                ? UUID.randomUUID().toString()
                : incomingCorrelationId;
        ClaimSnapshot orderHop = ClaimSnapshot.from("order-service", accessToken, correlationId);
        logHop(orderHop, "inventory-service");

        try {
            ReservationResult reservation = inventoryClient.reserve(
                    correlationId,
                    new ReservationRequest(orderId, 1));
            List<ClaimSnapshot> hops = new ArrayList<>();
            hops.add(orderHop);
            hops.addAll(reservation.hops());
            return new DelegationTrace(orderId, reservation.status(), hops);
        } catch (RuntimeException failure) {
            throw new DownstreamFailureException("inventory-service", correlationId, failure);
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
