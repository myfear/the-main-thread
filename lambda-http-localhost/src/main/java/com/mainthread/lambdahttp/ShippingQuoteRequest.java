package com.mainthread.lambdahttp;

public record ShippingQuoteRequest(String destination, int weightGrams, String speed, String customerTier) {
}
