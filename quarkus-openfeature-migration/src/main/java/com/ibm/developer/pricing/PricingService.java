package com.ibm.developer.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class PricingService {

    private static final String PRICING_ENGINE = "pricing-engine";
    private static final BigDecimal DYNAMIC_DISCOUNT_RATE = new BigDecimal("0.10");

    private final Flags flags;

    public PricingService(Flags flags) {
        this.flags = flags;
    }

    public Uni<Quote> createQuote(String tenantId, BigDecimal subtotal) {
        Flag.ComputationContext context = Flag.ComputationContext.of("targetingKey", tenantId);

        return flags.find(PRICING_ENGINE)
                .map(optionalFlag -> optionalFlag.orElseThrow())
                .chain(flag -> flag.compute(context)
                        .map(value -> calculate(tenantId, subtotal, value.asString(), flag.origin())));
    }

    private Quote calculate(String tenantId, BigDecimal subtotal, String pricingEngine, String flagOrigin) {
        BigDecimal normalizedSubtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountRate = switch (pricingEngine) {
            case "stable" -> BigDecimal.ZERO;
            case "dynamic" -> DYNAMIC_DISCOUNT_RATE;
            default -> throw new IllegalStateException("Unsupported pricing engine: " + pricingEngine);
        };
        BigDecimal discount = normalizedSubtotal.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);

        return new Quote(
                tenantId,
                pricingEngine,
                normalizedSubtotal,
                discount,
                normalizedSubtotal.subtract(discount),
                flagOrigin);
    }
}
