package com.requestwatch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
class PricingService {

    private final RequestWatchConfig config;
    private final Object refreshLock = new Object();

    private volatile SupplierQuote latestQuote = new SupplierQuote("startup", 1299);

    @Inject
    PricingService(RequestWatchConfig config) {
        this.config = config;
    }

    FastResponse fast() {
        SupplierQuote quote = latestQuote;
        return new FastResponse("fast", Thread.currentThread().getName(), quote.version(), quote.priceCents());
    }

    BlockingResponse blocking() {
        long start = System.nanoTime();
        SupplierQuote quote;
        synchronized (refreshLock) {
            quote = refreshQuote();
            latestQuote = quote;
        }
        return new BlockingResponse(
                "blocking",
                Thread.currentThread().getName(),
                quote.version(),
                quote.priceCents(),
                config.blocking().delayMillis(),
                elapsedMillis(start));
    }

    BlockingResponse blockingFixed() {
        long start = System.nanoTime();
        SupplierQuote quote = refreshQuote();
        synchronized (refreshLock) {
            latestQuote = quote;
        }
        return new BlockingResponse(
                "blocking-fixed",
                Thread.currentThread().getName(),
                quote.version(),
                quote.priceCents(),
                config.blocking().delayMillis(),
                elapsedMillis(start));
    }

    private SupplierQuote refreshQuote() {
        sleep(config.blocking().delayMillis());
        return new SupplierQuote("quote-" + System.nanoTime(), 1299);
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private static void sleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Quote refresh interrupted", e);
        }
    }

    private record SupplierQuote(String version, int priceCents) {
    }
}
