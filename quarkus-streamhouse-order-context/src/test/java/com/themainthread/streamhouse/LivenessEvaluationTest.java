package com.themainthread.streamhouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.themainthread.streamhouse.event.SourceHeartbeat;
import com.themainthread.streamhouse.projection.LivenessEvaluation;
import com.themainthread.streamhouse.projection.LivenessEvaluation.Signal;

class LivenessEvaluationTest {
    private final LivenessEvaluation liveness = new LivenessEvaluation(Clock.fixed(Fixtures.TIME, ZoneOffset.UTC),
            Duration.ofSeconds(30), Duration.ofSeconds(5));

    @Test
    void unknownRecentAndStaleAtTheExactBoundary() {
        var heartbeat = new SourceHeartbeat("shipping", Fixtures.TIME);
        assertEquals(Signal.UNKNOWN, liveness.evaluate(null, liveness.now()));
        assertEquals(Signal.RECENT, liveness.evaluate(heartbeat, liveness.now()));
        assertEquals(Signal.RECENT, liveness.evaluate(heartbeat, Fixtures.TIME.plusSeconds(30)));
        assertEquals(Signal.STALE, liveness.evaluate(heartbeat, Fixtures.TIME.plusSeconds(30).plusNanos(1)));
    }

    @Test
    void advancingTheInjectedClockNeedsNoSleep() {
        var later = new LivenessEvaluation(Clock.offset(Clock.fixed(Fixtures.TIME, ZoneOffset.UTC), Duration.ofSeconds(31)),
                Duration.ofSeconds(30), Duration.ofSeconds(5));
        assertEquals(Signal.STALE, later.evaluate(new SourceHeartbeat("shipping", Fixtures.TIME), later.now()));
        assertEquals(Signal.RECENT, later.evaluate(new SourceHeartbeat("inventory", later.now()), later.now()));
    }

    @Test
    void futureAllowanceIsBoundedAtIngestionAndReadTime() {
        var acceptable = new SourceHeartbeat("shipping", Fixtures.TIME.plusSeconds(5));
        var invalid = new SourceHeartbeat("shipping", Fixtures.TIME.plusSeconds(5).plusNanos(1));
        assertTrue(liveness.acceptable(acceptable));
        assertFalse(liveness.acceptable(invalid));
        assertEquals(Signal.RECENT, liveness.evaluate(acceptable, liveness.now()));
        assertEquals(Signal.UNKNOWN, liveness.evaluate(invalid, liveness.now()));
        assertEquals(Signal.UNKNOWN, liveness.evaluate(acceptable, Fixtures.TIME.minusSeconds(1)));
    }

    @Test
    void invalidConfigurationFailsBeforeProcessing() {
        assertThrows(IllegalArgumentException.class,
                () -> new LivenessEvaluation(Clock.systemUTC(), Duration.ZERO, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new LivenessEvaluation(Clock.systemUTC(), Duration.ofSeconds(30), Duration.ofSeconds(-1)));
    }
}
