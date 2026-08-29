package com.themainthread.planner.contract;

import java.time.Instant;

public record BookingDto(
        String id,
        String reference,
        String doorId,
        Instant startsAt,
        Instant endsAt,
        long version) {
}
