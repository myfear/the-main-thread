package com.themainthread.pricing;

import java.util.List;

public final class SampleSnapshots {

    private SampleSnapshots() {
    }

    public static PricingSnapshot sample() {
        return new PricingSnapshot(
                "quote-20260829-001",
                "gold",
                new ShippingAddress("DE", "10115"),
                List.of(
                        new LineItem("STORM-JACKET", 1, 12_999, 1_100),
                        new LineItem("HIKING-SOCKS", 3, 799, 450)));
    }
}
