package com.mainthread.lambdahttp;

import java.util.Locale;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShippingQuoteService {

    private static final Set<String> EU_DESTINATIONS = Set.of(
            "amsterdam", "barcelona", "berlin", "lisbon", "madrid", "paris", "porto", "rome", "vienna");

    public ShippingQuoteResponse preview(String destination,
            int weightGrams,
            String rawSpeed,
            String rawCustomerTier,
            String gatewayRequestId,
            String stage) {
        String normalizedDestination = normalizeDestination(destination);
        ShippingSpeed speed = ShippingSpeed.from(rawSpeed);
        String customerTier = normalizeCustomerTier(rawCustomerTier);
        int normalizedWeight = normalizeWeight(weightGrams);
        int quotedCents = quoteCents(normalizedWeight, speed, customerTier);

        return new ShippingQuoteResponse(
                normalizedDestination.toUpperCase(Locale.ROOT),
                speed.value(),
                customerTier,
                normalizedWeight,
                quotedCents,
                speed.estimatedBusinessDays(),
                fulfillmentRegion(normalizedDestination),
                defaultIfBlank(gatewayRequestId, "mock-event"),
                defaultIfBlank(stage, "$default"));
    }

    private String normalizeDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Destination is required.");
        }
        return destination.trim().toLowerCase(Locale.ROOT);
    }

    private int normalizeWeight(int weightGrams) {
        if (weightGrams < 100 || weightGrams > 5000) {
            throw new IllegalArgumentException("weightGrams must be between 100 and 5000.");
        }
        return weightGrams;
    }

    private String normalizeCustomerTier(String rawCustomerTier) {
        String normalized = defaultIfBlank(rawCustomerTier, "standard").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "standard", "silver", "gold" -> normalized;
            default -> throw new IllegalArgumentException(
                    "Unsupported customer tier '" + rawCustomerTier + "'. Use standard, silver, or gold.");
        };
    }

    private int quoteCents(int weightGrams, ShippingSpeed speed, String customerTier) {
        int base = 700;
        int weightCharge = Math.max(0, weightGrams - 500) / 100 * 45;
        int tierDiscount = switch (customerTier) {
            case "silver" -> 120;
            case "gold" -> 240;
            default -> 0;
        };
        return base + weightCharge + speed.surchargeCents() - tierDiscount;
    }

    private String fulfillmentRegion(String destination) {
        return EU_DESTINATIONS.contains(destination) ? "eu-central" : "global-export";
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
