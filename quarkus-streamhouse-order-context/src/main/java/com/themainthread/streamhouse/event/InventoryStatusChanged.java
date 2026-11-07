package com.themainthread.streamhouse.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryStatusChanged(UUID eventId, long orderId, long revision, Status status, Instant occurredAt) {
    public enum Status {
        RESERVED, REJECTED
    }

    public InventoryStatusChanged {
        EventValidation.required(eventId, "eventId");
        EventValidation.positive(orderId, "orderId");
        EventValidation.positive(revision, "revision");
        EventValidation.required(status, "status");
        EventValidation.required(occurredAt, "occurredAt");
    }
}
