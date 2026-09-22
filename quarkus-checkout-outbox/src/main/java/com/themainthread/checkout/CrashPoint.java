package com.themainthread.checkout;

import java.util.Locale;

import jakarta.ws.rs.BadRequestException;

public enum CrashPoint {
    NONE,
    COMMIT,
    KAFKA;

    static CrashPoint fromHeader(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "commit" -> COMMIT;
            case "kafka" -> KAFKA;
            default -> throw new BadRequestException("X-Crash-After must be commit or kafka");
        };
    }
}
