package com.themainthread.retry;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.faulttolerance.Retry;

import io.smallrye.faulttolerance.api.ExponentialBackoff;
import io.smallrye.faulttolerance.api.FibonacciBackoff;

@ApplicationScoped
public class QuoteClient {
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    public int once(URI carrier) throws IOException, InterruptedException {
        return getQuote(carrier);
    }

    @Retry(maxRetries = 3, delay = 0, jitter = 0, maxDuration = 2000, retryOn = CarrierUnavailable.class)
    public int immediate(URI carrier) throws IOException, InterruptedException {
        return getQuote(carrier);
    }

    @Retry(maxRetries = 3, delay = 100, jitter = 0, maxDuration = 2000, retryOn = CarrierUnavailable.class)
    public int fixed(URI carrier) throws IOException, InterruptedException {
        return getQuote(carrier);
    }

    @Retry(maxRetries = 3, delay = 100, jitter = 0, maxDuration = 2000, retryOn = CarrierUnavailable.class)
    @ExponentialBackoff(factor = 2, maxDelay = 400)
    public int exponential(URI carrier) throws IOException, InterruptedException {
        return getQuote(carrier);
    }

    @Retry(maxRetries = 3, delay = 100, jitter = 0, maxDuration = 2000, retryOn = CarrierUnavailable.class)
    @FibonacciBackoff(maxDelay = 400)
    public int fibonacci(URI carrier) throws IOException, InterruptedException {
        return getQuote(carrier);
    }

    private int getQuote(URI carrier) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(carrier).GET().timeout(Duration.ofSeconds(1)).build();
        int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        if (status == 503) {
            throw new CarrierUnavailable();
        }
        return status;
    }

    @PreDestroy
    void close() {
        client.close();
    }

    public static class CarrierUnavailable extends RuntimeException {
    }
}
