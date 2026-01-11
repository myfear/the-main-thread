package com.example;

import static io.restassured.RestAssured.given;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class IdempotencyTest {

    @Test
    public void testConcurrentIdempotentRequests() throws Exception {
        String key = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<CompletableFuture<String>> futures = IntStream.range(0, 10)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> given()
                        .header("Idempotency-Key", key)
                        .body("{\"item\":\"book\"}")
                        .post("/orders")
                        .then()
                        .extract()
                        .body()
                        .asString(), pool))
                .toList();

        // Wait for all futures to complete and collect results
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        pool.shutdown();
    }
}
