package com.example.billing.api;

public record BillingWindowResponse(
        String intervalStart,
        String intervalEnd,
        long durationHours,
        int calendarDays) {
}