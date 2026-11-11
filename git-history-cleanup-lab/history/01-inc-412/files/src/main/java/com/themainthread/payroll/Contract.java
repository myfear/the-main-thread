package com.themainthread.payroll;

public record Contract(String id, boolean includesOvertime, long weeklyMinutes) {
}
