package org.acme.carrier.bridge;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Path("/tracking")
@Produces(MediaType.APPLICATION_JSON)
public class TrackingResource {

    private final TrackingService trackingService;

    TrackingResource(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GET
    @Path("/{trackingId}")
    public RestResponse<TrackingResponse> tracking(@PathParam("trackingId") String trackingId) {
        return RestResponse.ok(trackingService.fetchTracking(trackingId));
    }

    @ServerExceptionMapper
    RestResponse<ApiError> mapTrackingNotFound(TrackingNotFoundException exception) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                new ApiError("tracking_not_found", exception.getMessage(), exception.downstreamStatus()));
    }

    @ServerExceptionMapper
    RestResponse<ApiError> mapCarrierUnavailable(CarrierUnavailableException exception) {
        return RestResponse.status(
                Response.Status.SERVICE_UNAVAILABLE,
                new ApiError("carrier_unavailable", exception.getMessage(), exception.downstreamStatus()));
    }

    @ServerExceptionMapper
    RestResponse<ApiError> mapCarrierTimeout(CarrierTimeoutException exception) {
        return RestResponse.status(
                Response.Status.GATEWAY_TIMEOUT,
                new ApiError("carrier_timeout", exception.getMessage(), exception.downstreamStatus()));
    }

    @ServerExceptionMapper
    RestResponse<ApiError> mapCarrierInvocation(CarrierInvocationException exception) {
        return RestResponse.status(
                Response.Status.BAD_GATEWAY,
                new ApiError("carrier_invocation_failed", exception.getMessage(), exception.downstreamStatus()));
    }
}
