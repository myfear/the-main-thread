package com.themainthread.streamhouse.event;

public final class EventValidation {
    private EventValidation() {
    }

    public static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    public static void positive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    public static void orderKey(String key, long orderId) {
        if (!Long.toString(orderId).equals(key)) {
            throw new IllegalArgumentException("Kafka key must match the decimal orderId");
        }
    }
}
