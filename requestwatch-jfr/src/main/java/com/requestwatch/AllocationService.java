package com.requestwatch;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
class AllocationService {

    private final RequestWatchConfig config;

    @Inject
    AllocationService(RequestWatchConfig config) {
        this.config = config;
    }

    AllocationResponse allocating() {
        long start = System.nanoTime();
        List<byte[]> buffers = new ArrayList<>(config.allocation().buffers());
        long checksum = 0;
        long allocatedBytes = 0;

        for (int i = 0; i < config.allocation().buffers(); i++) {
            byte[] buffer = new byte[config.allocation().bufferSizeBytes()];
            buffer[0] = (byte) i;
            buffer[buffer.length - 1] = (byte) (i * 31);
            buffers.add(buffer);
            checksum += Byte.toUnsignedLong(buffer[0]) + Byte.toUnsignedLong(buffer[buffer.length - 1]);
            allocatedBytes += buffer.length;
        }

        return new AllocationResponse(
                "allocating",
                Thread.currentThread().getName(),
                buffers.size(),
                config.allocation().bufferSizeBytes(),
                allocatedBytes,
                checksum,
                elapsedMillis(start));
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }
}
