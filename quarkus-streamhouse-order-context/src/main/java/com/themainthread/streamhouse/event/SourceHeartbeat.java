package com.themainthread.streamhouse.event;

import java.time.Instant;
import java.util.List;

public record SourceHeartbeat(String source, Instant emittedAt) {
    public static final List<String> SOURCES = List.of("checkout", "inventory", "shipping");

    public SourceHeartbeat {
        if (!SOURCES.contains(EventValidation.required(source, "source"))) {
            throw new IllegalArgumentException("Unknown heartbeat source");
        }
        EventValidation.required(emittedAt, "emittedAt");
    }

    public void validateKey(String key) {
        if (!source.equals(key)) {
            throw new IllegalArgumentException("Kafka key must match heartbeat source");
        }
    }
}
