package com.themainthread.streamhouse.projection;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.themainthread.streamhouse.event.SourceHeartbeat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LivenessEvaluation {
    public enum Signal {
        RECENT, STALE, UNKNOWN
    }

    private final Clock clock;
    private final Duration maxAge;
    private final Duration futureSkew;

    @Inject
    public LivenessEvaluation(Clock clock, ContextConfig config) {
        this(clock, config.heartbeatMaxAge(), config.heartbeatFutureSkew());
    }

    public LivenessEvaluation(Clock clock, Duration maxAge, Duration futureSkew) {
        if (maxAge.isNegative() || maxAge.isZero() || futureSkew.isNegative()) {
            throw new IllegalArgumentException("Heartbeat max age must be positive and future skew non-negative");
        }
        this.clock = clock;
        this.maxAge = maxAge;
        this.futureSkew = futureSkew;
    }

    public boolean acceptable(SourceHeartbeat heartbeat) {
        return !heartbeat.emittedAt().isAfter(clock.instant().plus(futureSkew));
    }

    public Signal evaluate(SourceHeartbeat heartbeat, Instant now) {
        if (heartbeat == null || heartbeat.emittedAt().isAfter(now.plus(futureSkew))) {
            return Signal.UNKNOWN;
        }
        return heartbeat.emittedAt().isBefore(now.minus(maxAge)) ? Signal.STALE : Signal.RECENT;
    }

    public Instant now() {
        return clock.instant();
    }
}
