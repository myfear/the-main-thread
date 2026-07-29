package com.mainthread.tidalmesh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrderSessionTest {

    @Test
    void keepsIndependentCountsPerOrder() {
        OrderSession session = new OrderSession();

        assertEquals(1, session.record("ORD-42"));
        assertEquals(2, session.record("ORD-42"));
        assertEquals(1, session.record("ORD-99"));
    }
}

