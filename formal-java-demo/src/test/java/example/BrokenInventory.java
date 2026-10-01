package example;

import java.util.concurrent.atomic.AtomicInteger;

final class BrokenInventory {
    private final AtomicInteger available;
    private final Runnable afterCheck;

    BrokenInventory(int available, Runnable afterCheck) {
        this.available = new AtomicInteger(available);
        this.afterCheck = afterCheck;
    }

    boolean reserve() {
        if (available.get() <= 0) {
            return false;
        }
        // The test pauses both callers after the check, before either decrement.
        afterCheck.run();
        available.decrementAndGet();
        return true;
    }

    int available() {
        return available.get();
    }
}
