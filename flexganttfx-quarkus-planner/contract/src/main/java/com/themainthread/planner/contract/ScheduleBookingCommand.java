package com.themainthread.planner.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record ScheduleBookingCommand(
        @NotBlank String doorId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @PositiveOrZero long expectedVersion) {
}
