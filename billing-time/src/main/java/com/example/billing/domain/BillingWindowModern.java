package com.example.billing.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class BillingWindowModern {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final ZoneId zone;

    public BillingWindowModern(LocalDate startDate, LocalDate endDate, ZoneId zone) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.zone = zone;
    }

    public Duration toDuration() {
        ZonedDateTime start = startDate.atStartOfDay(zone);
        ZonedDateTime end = endDate.plusDays(1).atStartOfDay(zone);
        return Duration.between(start, end);
    }
}
