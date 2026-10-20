package com.themainthread.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class GiftRecoveryTest {
    @Inject
    DatabaseExperiments database;

    @Test
    void competingWorkersCommitOneLocalEffect() throws Exception {
        Instant due = Instant.parse("2026-12-24T00:00:00Z");
        var job = database.createJob(due);
        var otherJob = database.createJob(due);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return database.attempt(job, due, false);
            });
            var second = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return database.attempt(job, due, false);
            });
            assertEquals(1, first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, database.effectCount(job));
        assertEquals(0, database.effectCount(otherJob));
    }

    @Test
    void rollbackLeavesTheJobRecoverable() {
        Instant due = Instant.parse("2026-12-24T00:00:00Z");
        var job = database.createJob(due);
        assertEquals(0, database.attempt(job, due.minusNanos(1000), false));
        assertEquals(1, database.attempt(job, due, true));
        assertEquals(0, database.effectCount(job));
        assertEquals(1, database.attempt(job, due.plusSeconds(600), false));
        assertEquals(0, database.attempt(job, due.plusSeconds(601), false));
        assertEquals(1, database.effectCount(job));
    }
}
