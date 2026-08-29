package com.themainthread.pricing;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuoteCalculator {

    public QuoteDecision quote(PricingSnapshot snapshot) {
        long subtotalCents = snapshot.lines().stream()
                .mapToLong(line -> Math.multiplyExact(line.quantity(), line.unitPriceCents()))
                .sum();
        int totalWeightGrams = snapshot.lines().stream()
                .mapToInt(line -> Math.multiplyExact(line.quantity(), line.weightGrams()))
                .sum();

        long shippingCents = shippingCents(snapshot.destination().countryCode(), totalWeightGrams);
        int deliveryDays = totalWeightGrams > 10_000 ? 4 : 2;

        return new QuoteDecision(
                snapshot.snapshotId(),
                subtotalCents,
                shippingCents,
                Math.addExact(subtotalCents, shippingCents),
                deliveryDays);
    }

    private long shippingCents(String countryCode, int totalWeightGrams) {
        long baseShippingCents = "DE".equals(countryCode) ? 799 : 1_499;
        return totalWeightGrams > 10_000 ? baseShippingCents + 500 : baseShippingCents;
    }
}
