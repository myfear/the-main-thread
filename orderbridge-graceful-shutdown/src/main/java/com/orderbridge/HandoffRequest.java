package com.orderbridge;

public record HandoffRequest(String orderId, long amountCents) {
}
