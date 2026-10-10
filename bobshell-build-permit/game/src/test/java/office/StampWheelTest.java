package office;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StampWheelTest {
    @Test
    void movesBetweenCounters() {
        assertEquals(5, StampWheel.rotate(2, 3));
        assertEquals(4, StampWheel.rotate(4, 0));
    }

    @Test
    void wrapsForward() {
        assertEquals(1, StampWheel.rotate(9, 2));
        assertEquals(0, StampWheel.rotate(8, 12));
    }

    @Test
    void wrapsBackward() {
        assertEquals(9, StampWheel.rotate(0, -1));
        assertEquals(9, StampWheel.rotate(2, -13));
    }
}
