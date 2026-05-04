package dev.novadeck;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NovaDeckToolCountsTest {

    @Test
    void catalogSizeMatchesImplementationPlan() {
        assertEquals(50, NovaDeckToolCounts.TOTAL_TOOLS);
    }
}
