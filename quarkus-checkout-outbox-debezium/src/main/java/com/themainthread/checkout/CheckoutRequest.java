package com.themainthread.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CheckoutRequest(
        @NotBlank String sku,
        @Positive int quantity) {
}
