package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sageserpent.americium.java.Trials;
import com.sageserpent.americium.junit5.java.TrialsTest;

class InventoryPropertyTest {
    private static final Trials<Integer> stocks = Trials.api().integers(0, 10_000);

    @TrialsTest(trials = "stocks", casesLimit = 1_000)
    void transitionPreservesNonnegativeStock(int stock) {
        int next = Inventory.nextAvailable(stock);

        assertTrue(next >= 0, "Stock must remain non-negative");
        assertTrue(next <= stock, "Reservation must not increase stock");
        if (stock > 0) {
            assertEquals(stock - 1, next);
        } else {
            assertEquals(0, next);
        }
    }
}
