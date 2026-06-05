package com.requestwatch;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
class StartupWarmup {

    private static final Logger LOG = Logger.getLogger(StartupWarmup.class);

    private final RequestWatchConfig config;

    @Inject
    StartupWarmup(RequestWatchConfig config) {
        this.config = config;
    }

    void onStart(@Observes StartupEvent ignored) {
        if (!config.startup().enabled()) {
            return;
        }

        List<byte[]> buffers = new ArrayList<>(config.startup().buffers());
        long allocatedBytes = 0;
        for (int i = 0; i < config.startup().buffers(); i++) {
            byte[] buffer = new byte[config.startup().bufferSizeBytes()];
            buffer[0] = (byte) i;
            buffers.add(buffer);
            allocatedBytes += buffer.length;
        }

        sleep(config.startup().delayMillis());
        LOG.infof("Startup warmup allocated %,d bytes across %d buffers", allocatedBytes, buffers.size());
    }

    private static void sleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Startup warmup interrupted", e);
        }
    }
}
