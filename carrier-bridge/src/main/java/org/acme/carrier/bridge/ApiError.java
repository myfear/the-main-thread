package org.acme.carrier.bridge;

public record ApiError(String code, String message, Integer downstreamStatus) {
}
