package com.mainthread.lambdahttp;

public record ShippingQuoteResponse(
        String destination,
        String speed,
        String customerTier,
        int weightGrams,
        int quotedCents,
        int estimatedBusinessDays,
        String fulfillmentRegion,
        String gatewayRequestId,
        String stage) {
}
