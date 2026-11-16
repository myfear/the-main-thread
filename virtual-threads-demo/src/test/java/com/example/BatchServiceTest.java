package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;

class BatchServiceTest {
    @Test
    void cancelsAnInterruptedChildOnDeadlineAndReleasesCapacity() {
        var service = new BatchService();
        var interrupted = new AtomicBoolean();
        Callable<Boolean> child = () -> {
            try {
                Thread.sleep(10000);
                return true;
            } catch (InterruptedException e) {
                interrupted.set(true);
                throw e;
            }
        };
        var error = assertThrows(WebApplicationException.class, () -> service.execute(List.of(child), 100));
        assertEquals(504, error.getResponse().getStatus());
        assertTrue(interrupted.get());
        assertEquals(1, service.run(1, 0, 1000).completed());
    }

    @Test
    void reportsFailureAndRetainsInterruptStatus() {
        var service = new BatchService();
        assertThrows(InternalServerErrorException.class,
                () -> service.execute(List.of(() -> { throw new IllegalStateException("test"); }), 1000));
        Thread.currentThread().interrupt();
        try {
            assertThrows(ServiceUnavailableException.class, () -> service.run(1, 1000, 2000));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(1, service.run(1, 0, 1000).completed());
    }

    @Test
    void rejectsTheFifthBatchWithoutQueuing() throws Exception {
        var service = new BatchService();
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = new ArrayList<java.util.concurrent.Future<BatchService.Batch>>();
            for (int i = 0; i < 4; i++) {
                requests.add(callers.submit(() -> service.execute(List.of(() -> {
                    entered.countDown();
                    release.await();
                    return true;
                }), 5000)));
            }
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertThrows(ServiceUnavailableException.class, () -> service.run(1, 0, 1000));
            } finally {
                release.countDown();
            }
            for (var request : requests) {
                assertEquals(1, request.get().completed());
            }
        }
    }
}
