package com.themainthread.planner.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.themainthread.planner.contract.BoardDto;
import com.themainthread.planner.contract.BookingDto;
import com.themainthread.planner.contract.DockDoorDto;
import com.themainthread.planner.contract.ScheduleProblem;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleResponseReducerTest {

    private final ScheduleResponseReducer reducer = new ScheduleResponseReducer();

    @Test
    void commitsSuccessfulSchedule() {
        BookingDto booking = sampleBooking();
        ScheduleResponseReducer.Decision decision = reducer.decide(200, booking, null);
        assertEquals(ScheduleResponseReducer.Action.COMMIT, decision.action());
        assertEquals(booking, decision.booking());
    }

    @Test
    void restoresOverlappingProposal() {
        ScheduleProblem problem = new ScheduleProblem("OVERLAPPING_BOOKING", "occupied", null);
        ScheduleResponseReducer.Decision decision = reducer.decide(409, null, problem);
        assertEquals(ScheduleResponseReducer.Action.RESTORE, decision.action());
    }

    @Test
    void replacesStaleBooking() {
        BookingDto current = new BookingDto(
                "booking-42",
                "TRUCK-1042",
                "door-3",
                Instant.parse("2026-08-20T08:00:00Z"),
                Instant.parse("2026-08-20T09:30:00Z"),
                4);
        ScheduleProblem problem = new ScheduleProblem("STALE_BOOKING", "stale", current);
        ScheduleResponseReducer.Decision decision = reducer.decide(409, null, problem);
        assertEquals(ScheduleResponseReducer.Action.REPLACE, decision.action());
        assertEquals(current, decision.booking());
    }

    private static BookingDto sampleBooking() {
        return new BookingDto(
                "booking-42",
                "TRUCK-1042",
                "door-3",
                Instant.parse("2026-08-20T08:00:00Z"),
                Instant.parse("2026-08-20T09:30:00Z"),
                3);
    }
}
