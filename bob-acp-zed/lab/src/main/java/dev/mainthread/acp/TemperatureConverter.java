package dev.mainthread.acp;

public final class TemperatureConverter {

    private TemperatureConverter() {
    }

    public static int celsiusToFahrenheit(int celsius) {
        return celsius * 9 / 5;
    }
}
