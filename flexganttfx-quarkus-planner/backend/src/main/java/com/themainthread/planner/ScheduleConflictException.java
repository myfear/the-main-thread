package com.themainthread.planner;

import com.themainthread.planner.contract.BookingDto;
import com.themainthread.planner.contract.ScheduleProblem;
import jakarta.ws.rs.core.Response;

public final class ScheduleConflictException extends RuntimeException {

    private final Response.Status status;
    private final String code;
    private final BookingDto currentBooking;

    private ScheduleConflictException(Response.Status status, String code, String message, BookingDto currentBooking) {
        super(message);
        this.status = status;
        this.code = code;
        this.currentBooking = currentBooking;
    }

    public static ScheduleConflictException overlapping(String reference) {
        return new ScheduleConflictException(
                Response.Status.CONFLICT,
                "OVERLAPPING_BOOKING",
                "Door already has a booking that overlaps " + reference,
                null);
    }

    public static ScheduleConflictException stale(BookingDto currentBooking) {
        return new ScheduleConflictException(
                Response.Status.CONFLICT,
                "STALE_BOOKING",
                "Booking was updated by another writer",
                currentBooking);
    }

    public Response.Status status() {
        return status;
    }

    public ScheduleProblem problem() {
        return new ScheduleProblem(code, getMessage(), currentBooking);
    }
}
