package com.requestwatch;

public record AllocationResponse(
        String endpoint,
        String threadName,
        int bufferCount,
        int bufferSizeBytes,
        long allocatedBytes,
        long checksum,
        long elapsedMs) {
}
