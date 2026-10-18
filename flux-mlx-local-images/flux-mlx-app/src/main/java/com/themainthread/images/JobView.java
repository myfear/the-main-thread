package com.themainthread.images;

import java.time.Instant;
import java.util.UUID;

public record JobView(UUID id, State state, String prompt, int width, int height,
        long seed, String model, int steps, Instant submittedAt, Instant startedAt,
        Instant finishedAt, Long durationMs, String error, String imageUrl) {

    public enum State {
        QUEUED, RUNNING, SUCCEEDED, FAILED, UNKNOWN
    }

    public boolean terminal() {
        return state == State.SUCCEEDED || state == State.FAILED || state == State.UNKNOWN;
    }
}
