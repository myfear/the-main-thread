package com.pixel.service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class ConversionGate {
    private final Semaphore slots;

    public ConversionGate(ImageLimits limits) {
        if (limits.maxConcurrent() < 1 || limits.maxPixels() < 1 || limits.maxBlockSize() < 1) {
            throw new IllegalArgumentException("Image limits must be positive");
        }
        slots = new Semaphore(limits.maxConcurrent());
    }

    public Permit enter() {
        if (!slots.tryAcquire()) {
            throw new WebApplicationException(Response.status(503).header("Retry-After", "1").build());
        }
        return new Permit();
    }

    public final class Permit implements AutoCloseable {
        private final AtomicBoolean released = new AtomicBoolean();

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                slots.release();
            }
        }
    }
}
