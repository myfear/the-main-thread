package example;

import java.util.concurrent.atomic.AtomicInteger;

public final class Inventory {
    private final AtomicInteger available;

    public Inventory(int available) {
        if (available < 0) {
            throw new IllegalArgumentException("Initial inventory must be non-negative");
        }
        this.available = new AtomicInteger(available);
    }

    public boolean reserve() {
        while (true) {
            int current = available.get();
            if (current <= 0) {
                return false;
            }
            int next = nextAvailable(current);
            if (available.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    public int available() {
        return available.get();
    }

    static int nextAvailable(int current) {
        return current > 0 ? current - 1 : current;
    }
}
