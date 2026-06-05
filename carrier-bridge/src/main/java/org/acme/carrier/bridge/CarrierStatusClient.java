package org.acme.carrier.bridge;

import java.net.URI;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

@Path("/carrier-api")
@RegisterRestClient(configKey = "carrier-api")
@RegisterProvider(CarrierAuthFilter.class)
public interface CarrierStatusClient {

    @GET
    @Path("/tracking/{trackingId}")
    CarrierTrackingPayload getTracking(@PathParam("trackingId") String trackingId);

    @ClientExceptionMapper
    static RuntimeException toException(Response response, URI uri) {
        return switch (response.getStatus()) {
            case 404 -> new TrackingNotFoundException(uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1));
            case 503 -> new CarrierUnavailableException();
            default -> null;
        };
    }
}
