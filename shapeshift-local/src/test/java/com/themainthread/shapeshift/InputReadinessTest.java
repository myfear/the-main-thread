package com.themainthread.shapeshift;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class InputReadinessTest {
    private final InputReadiness readiness = new InputReadiness();

    @Test
    void waitsForCommandContentAndDanglingDetails() {
        for (String text : new String[] { "remind me", "dinner with", "split", "buy", "set a", "I want to",
                "please remind me to", "Lunch with Ben at", "Tasks: test,", "Split 120 between", "Meet at 10:" }) {
            assertNotNull(readiness.waitingReason(text), text);
        }
    }

    @Test
    void waitsForSplitOperandsWithoutMatchingGenericSharing() {
        assertNotNull(readiness.waitingReason("Split 12"));
        assertNotNull(readiness.waitingReason("Divide 50 among 0"));
        assertNotNull(readiness.waitingReason("Share 30 among 2.5"));
        assertNull(readiness.waitingReason("Share an idea about Java"));
        assertNull(readiness.waitingReason("Split 120.50 between 3"));
    }

    @Test
    void permitsCompleteContentWithEditableCalendarDetails() {
        for (String text : new String[] { "Remind me to pay rent", "Dinner with Priya", "Lunch with Ben at 10:00",
                "25 min focus", "Buy milk and eggs", "Today was good", "What is this for?", "Hi" }) {
            assertNull(readiness.waitingReason(text), text);
        }
    }
}
