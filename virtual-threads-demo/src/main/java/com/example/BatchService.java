package com.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class BatchService {
    private final Semaphore batches = new Semaphore(4);

    public Batch run(int count, int delayMs, int timeoutMs) {
        if (count < 1 || count > 32 || delayMs < 0 || delayMs > 2000 || timeoutMs < 1 || timeoutMs > 5000) {
            throw new BadRequestException("count: 1..32, delayMs: 0..2000, timeoutMs: 1..5000");
        }
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(() -> {
                Thread.sleep(delayMs);
                return Thread.currentThread().isVirtual();
            });
        }
        return execute(tasks, timeoutMs);
    }

    Batch execute(List<Callable<Boolean>> tasks, int timeoutMs) {
        if (!batches.tryAcquire()) {
            throw new ServiceUnavailableException("Four batches are already running", 1L);
        }
        long start = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = executor.invokeAll(tasks, timeoutMs, TimeUnit.MILLISECONDS);
            boolean allVirtual = true;
            for (var future : futures) {
                if (future.isCancelled()) {
                    throw new WebApplicationException("Batch deadline exceeded", 504);
                }
                allVirtual &= future.get();
            }
            return new Batch(tasks.size(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), allVirtual);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Request interrupted");
        } catch (ExecutionException e) {
            throw new InternalServerErrorException("Subtask failed", e.getCause());
        } finally {
            batches.release();
        }
    }

    public record Batch(int completed, long elapsedMs, boolean allVirtual) {
    }
}
