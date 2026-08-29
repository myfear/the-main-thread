package com.themainthread.planner.desktop;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.flexganttfx.model.ActivityRef;
import com.flexganttfx.model.Layer;
import com.flexganttfx.model.util.TimeInterval;
import com.flexganttfx.view.graphics.ActivityEvent;
import com.themainthread.planner.contract.BookingDto;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScheduleProposalCoordinatorTest {

    private static final Instant START = Instant.parse("2026-08-20T08:00:00Z");
    private static final Instant END = Instant.parse("2026-08-20T09:00:00Z");

    @Test
    void verticalDragUsesTheEventTargetRow() {
        Layer layer = new Layer("Bookings");
        DockDoorRow oldRow = new DockDoorRow("door-1", "Door 1");
        DockDoorRow newRow = new DockDoorRow("door-2", "Door 2");
        BookingActivity activity = bookingActivity();
        ActivityRef<BookingActivity> activityRef = new ActivityRef<>(oldRow, layer, activity);
        ActivityEvent event = new ActivityEvent(
                activityRef,
                null,
                ActivityEvent.DRAG_FINISHED,
                oldRow,
                newRow,
                new TimeInterval(START, END));

        assertSame(newRow, ScheduleProposalCoordinator.targetRow(event));
    }

    @Test
    void horizontalDragKeepsTheActivityRow() {
        Layer layer = new Layer("Bookings");
        DockDoorRow row = new DockDoorRow("door-1", "Door 1");
        BookingActivity activity = bookingActivity();
        ActivityRef<BookingActivity> activityRef = new ActivityRef<>(row, layer, activity);
        ActivityEvent event = new ActivityEvent(
                activityRef,
                null,
                ActivityEvent.HORIZONTAL_DRAG_FINISHED,
                new TimeInterval(START, END));

        assertSame(row, ScheduleProposalCoordinator.targetRow(event));
    }

    private static BookingActivity bookingActivity() {
        return new BookingActivity(new BookingDto(
                "booking-42",
                "TRUCK-1042",
                "door-1",
                START,
                END,
                0));
    }
}
