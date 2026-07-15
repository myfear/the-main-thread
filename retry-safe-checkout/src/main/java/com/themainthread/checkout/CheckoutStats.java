package com.themainthread.checkout;

public record CheckoutStats(
        long orders,
        int fulfillmentDispatches,
        int processing) {
}
