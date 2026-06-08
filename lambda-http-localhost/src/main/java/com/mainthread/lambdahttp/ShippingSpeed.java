package com.mainthread.lambdahttp;

import java.util.Arrays;

enum ShippingSpeed {
    STANDARD("standard", 4, 0),
    EXPRESS("express", 2, 350),
    OVERNIGHT("overnight", 1, 900);

    private final String value;
    private final int estimatedBusinessDays;
    private final int surchargeCents;

    ShippingSpeed(String value, int estimatedBusinessDays, int surchargeCents) {
        this.value = value;
        this.estimatedBusinessDays = estimatedBusinessDays;
        this.surchargeCents = surchargeCents;
    }

    String value() {
        return value;
    }

    int estimatedBusinessDays() {
        return estimatedBusinessDays;
    }

    int surchargeCents() {
        return surchargeCents;
    }

    static ShippingSpeed from(String rawSpeed) {
        String normalized = rawSpeed == null ? "" : rawSpeed.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(speed -> speed.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported speed '" + rawSpeed
                        + "'. Use standard, express, or overnight."));
    }
}
