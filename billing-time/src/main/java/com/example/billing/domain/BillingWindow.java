package com.example.billing.domain;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.PeriodType;

public class BillingWindow {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final DateTimeZone customerZone;

    public BillingWindow(LocalDate startDate, LocalDate endDate, DateTimeZone customerZone) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.customerZone = customerZone;
    }

    public Interval toInterval() {
        DateTime start = startDate.toDateTimeAtStartOfDay(customerZone);
        DateTime end = endDate.plusDays(1).toDateTimeAtStartOfDay(customerZone);
        return new Interval(start, end);
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public DateTimeZone getCustomerZone() {
        return customerZone;
    }

    public Period toCalendarPeriod() {
        return new Period(
                startDate.toDateTimeAtStartOfDay(customerZone),
                endDate.plusDays(1).toDateTimeAtStartOfDay(customerZone),
                PeriodType.days());
    }

}