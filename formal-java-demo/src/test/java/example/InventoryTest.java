package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class InventoryTest {
    @Test
    void reservesTheLastItem() {
        Inventory inventory = new Inventory(1);

        assertTrue(inventory.reserve());
        assertEquals(0, inventory.available());
        assertFalse(inventory.reserve());
        assertEquals(0, inventory.available());
    }

    @Test
    void rejectsReservationWhenEmpty() {
        Inventory inventory = new Inventory(0);

        assertFalse(inventory.reserve());
        assertEquals(0, inventory.available());
    }

    @Test
    void rejectsNegativeInitialInventory() {
        assertThrows(IllegalArgumentException.class, () -> new Inventory(-1));
    }

    @Test
    void transitionHandlesIntegerBoundaries() {
        assertEquals(0, Inventory.nextAvailable(0));
        assertEquals(0, Inventory.nextAvailable(1));
        assertEquals(Integer.MAX_VALUE - 1, Inventory.nextAvailable(Integer.MAX_VALUE));
    }

    @Test
    void exactlyOneConcurrentCallerReservesTheLastItem() throws Exception {
        Inventory inventory = new Inventory(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS), "Start signal must be released");
            return inventory.reserve();
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both callers must be ready");
            start.countDown();

            boolean firstReserved = first.get(5, TimeUnit.SECONDS);
            boolean secondReserved = second.get(5, TimeUnit.SECONDS);
            assertEquals(1, (firstReserved ? 1 : 0) + (secondReserved ? 1 : 0));
            assertEquals(0, inventory.available());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
