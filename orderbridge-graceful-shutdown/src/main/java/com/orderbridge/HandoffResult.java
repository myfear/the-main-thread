package com.orderbridge;

public record HandoffResult(String orderId, String status, long elapsedMs) {
}
