package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class BrokenInventoryTest {
    @Test
    void demonstratesCheckThenActRace() throws Exception {
        CountDownLatch bothChecked = new CountDownLatch(2);
        BrokenInventory inventory = new BrokenInventory(1, () -> {
            bothChecked.countDown();
            try {
                assertTrue(bothChecked.await(5, TimeUnit.SECONDS), "Both callers must pass the stock check");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while forcing the race", exception);
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(inventory::reserve);
            Future<Boolean> second = executor.submit(inventory::reserve);

            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertTrue(second.get(5, TimeUnit.SECONDS));
            assertEquals(-1, inventory.available(), "This assertion demonstrates the deliberately broken protocol");
        } finally {
            executor.shutdownNow();
        }
    }
}
