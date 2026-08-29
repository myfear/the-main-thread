package dev.mainthread.acp;

public final class TemperatureConverterTest {

    private TemperatureConverterTest() {
    }

    public static void main(String[] args) {
        assertEquals(32, TemperatureConverter.celsiusToFahrenheit(0));
        assertEquals(212, TemperatureConverter.celsiusToFahrenheit(100));
        assertEquals(-40, TemperatureConverter.celsiusToFahrenheit(-40));
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }
}
