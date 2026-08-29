package com.themainthread.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReservationRequest(
        @NotBlank String sku,
        @NotNull @Min(1) Integer quantity) {
}
