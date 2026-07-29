package dev.mainthread.delegation.order;

public class DownstreamFailureException extends RuntimeException {

    private final String correlationId;

    public DownstreamFailureException(String target, String correlationId, Throwable cause) {
        super("Call to " + target + " failed", cause);
        this.correlationId = correlationId;
    }

    public String correlationId() {
        return correlationId;
    }
}
