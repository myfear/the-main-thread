package com.themainthread.gluhwein;

import java.time.Instant;
import java.util.UUID;

public record Order(UUID id, int number, String name, Drink drink, int quantity,
        Status status, long revision, Instant updatedAt) {

    public enum Drink {
        RED, WHITE, APPLE
    }

    public enum Status {
        QUEUED, PREPARING, READY
    }
}
