package com.example.billing.api;

import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.Period;

import com.example.billing.domain.BillingWindow;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

@Path("/billing/window")
public class BillingWindowResource {

    @GET
    public BillingWindowResponse calculate(
            @QueryParam("start") String start,
            @QueryParam("end") String end,
            @QueryParam("zone") String zoneId) {

        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);
        DateTimeZone zone = DateTimeZone.forID(zoneId);

        BillingWindow window = new BillingWindow(startDate, endDate, zone);
        Interval interval = window.toInterval();

        Period calendarPeriod = window.toCalendarPeriod();

        return new BillingWindowResponse(
                interval.getStart().toString(),
                interval.getEnd().toString(),
                interval.toDuration().getStandardHours(),
                calendarPeriod.getDays());
    }
}