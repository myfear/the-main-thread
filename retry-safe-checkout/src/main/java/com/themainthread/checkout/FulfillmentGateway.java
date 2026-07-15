package com.themainthread.checkout;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ServiceUnavailableException;

@ApplicationScoped
public class FulfillmentGateway {

    private final CheckoutConfig config;
    private final AtomicInteger dispatches = new AtomicInteger();
    private final AtomicInteger processing = new AtomicInteger();

    public FulfillmentGateway(CheckoutConfig config) {
        this.config = config;
    }

    public String dispatch(CheckoutRequest request) {
        processing.incrementAndGet();
        try {
            Thread.sleep(config.processingDelay().toMillis());
            return "FUL-%04d".formatted(dispatches.incrementAndGet());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Fulfillment dispatch was interrupted");
        } finally {
            processing.decrementAndGet();
        }
    }

    public int dispatchCount() {
        return dispatches.get();
    }

    public int processingCount() {
        return processing.get();
    }
}
