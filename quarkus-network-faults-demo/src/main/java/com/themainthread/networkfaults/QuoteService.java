package com.themainthread.networkfaults;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class QuoteService {

    private final CarrierClient carrier;

    public QuoteService(@RestClient CarrierClient carrier) {
        this.carrier = carrier;
    }

    @Timeout(300)
    @Fallback(fallbackMethod = "unavailable")
    public QuoteResult once(String destination) {
        return QuoteResult.available(carrier.quote(destination));
    }

    @Timeout(300)
    @Retry(maxRetries = 2, delay = 100, jitter = 0, maxDuration = 2_000,
            retryOn = { CarrierHttpException.class, ProcessingException.class, TimeoutException.class })
    @Fallback(fallbackMethod = "unavailable")
    public QuoteResult retrying(String destination) {
        return QuoteResult.available(carrier.quote(destination));
    }

    QuoteResult unavailable(String destination, Throwable cause) {
        return QuoteResult.unavailable(cause);
    }
}
