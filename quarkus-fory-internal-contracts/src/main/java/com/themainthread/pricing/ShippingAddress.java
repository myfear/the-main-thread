package com.themainthread.pricing;

import io.quarkiverse.fory.ForySerialization;

@ForySerialization(classId = 257)
public record ShippingAddress(
        String countryCode,
        String postalCode) {
}
