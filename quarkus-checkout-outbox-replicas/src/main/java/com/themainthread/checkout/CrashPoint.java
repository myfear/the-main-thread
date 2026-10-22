package com.themainthread.checkout;

import java.util.Locale;

import jakarta.ws.rs.BadRequestException;

public enum CrashPoint {
    NONE,
    COMMIT,
    CLAIM,
    KAFKA;

    static CrashPoint fromHeader(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "commit" -> COMMIT;
            case "claim" -> CLAIM;
            case "kafka" -> KAFKA;
            default -> throw new BadRequestException("X-Crash-After must be commit, claim, or kafka");
        };
    }
}
