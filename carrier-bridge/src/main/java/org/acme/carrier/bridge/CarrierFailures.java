package org.acme.carrier.bridge;

abstract class CarrierFailure extends RuntimeException {

    private final Integer downstreamStatus;

    CarrierFailure(String message, Integer downstreamStatus) {
        super(message);
        this.downstreamStatus = downstreamStatus;
    }

    CarrierFailure(String message, Integer downstreamStatus, Throwable cause) {
        super(message, cause);
        this.downstreamStatus = downstreamStatus;
    }

    Integer downstreamStatus() {
        return downstreamStatus;
    }
}

final class TrackingNotFoundException extends CarrierFailure {

    TrackingNotFoundException(String trackingId) {
        super("Carrier API could not find tracking ID '%s'.".formatted(trackingId), 404);
    }
}

final class CarrierUnavailableException extends CarrierFailure {

    CarrierUnavailableException() {
        super("Carrier API is temporarily unavailable.", 503);
    }
}

final class CarrierTimeoutException extends CarrierFailure {

    CarrierTimeoutException(Throwable cause) {
        super("Carrier API did not respond before the outbound read timeout.", null, cause);
    }
}

final class CarrierInvocationException extends CarrierFailure {

    CarrierInvocationException(Throwable cause) {
        super("Carrier API call failed before a usable response was returned.", null, cause);
    }
}
