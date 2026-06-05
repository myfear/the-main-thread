package org.acme.carrier.bridge;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;

@ApplicationScoped
class TrackingService {

    private final CarrierStatusClient carrierStatusClient;

    TrackingService(@RestClient CarrierStatusClient carrierStatusClient) {
        this.carrierStatusClient = carrierStatusClient;
    }

    @Retry(retryOn = CarrierUnavailableException.class)
    TrackingResponse fetchTracking(String trackingId) {
        try {
            CarrierTrackingPayload payload = carrierStatusClient.getTracking(trackingId);
            return new TrackingResponse(
                    payload.trackingId(),
                    payload.carrier(),
                    payload.status(),
                    payload.lastUpdated());
        } catch (ProcessingException e) {
            if (hasTimeoutCause(e)) {
                throw new CarrierTimeoutException(e);
            }
            throw new CarrierInvocationException(e);
        }
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if ((current instanceof SocketTimeoutException) || (current instanceof TimeoutException)) {
                return true;
            }
            String simpleName = current.getClass().getSimpleName();
            if (simpleName.contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
