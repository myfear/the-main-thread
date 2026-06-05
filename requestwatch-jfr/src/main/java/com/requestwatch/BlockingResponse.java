package com.requestwatch;

public record BlockingResponse(
        String endpoint,
        String threadName,
        String quoteVersion,
        int priceCents,
        long simulatedDelayMs,
        long elapsedMs) {
}
