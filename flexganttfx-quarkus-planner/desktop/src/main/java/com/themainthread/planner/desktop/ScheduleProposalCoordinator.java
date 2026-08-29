package com.themainthread.planner.desktop;

import com.flexganttfx.model.Layer;
import com.flexganttfx.model.Row;
import com.flexganttfx.view.graphics.ActivityEvent;
import com.themainthread.planner.contract.BookingDto;
import com.themainthread.planner.contract.ScheduleBookingCommand;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javafx.application.Platform;

final class ScheduleProposalCoordinator {

    private final Layer bookingsLayer;
    private final BoardClient boardClient;
    private final ScheduleResponseReducer reducer = new ScheduleResponseReducer();
    private final Consumer<String> statusSink;
    private final Set<String> pendingBookings = new HashSet<>();
    private Map<String, DockDoorRow> rowsByDoorId = Collections.emptyMap();
    private final Map<String, DockDoorRow> currentRowsByBookingId = new HashMap<>();

    ScheduleProposalCoordinator(
            Layer bookingsLayer,
            BoardClient boardClient,
            Consumer<String> statusSink) {
        this.bookingsLayer = bookingsLayer;
        this.boardClient = boardClient;
        this.statusSink = statusSink;
    }

    void setBoardState(
            Map<String, DockDoorRow> rowsByDoorId,
            Map<String, BookingActivity> activitiesById) {
        this.rowsByDoorId = Map.copyOf(rowsByDoorId);
        currentRowsByBookingId.clear();
        activitiesById.forEach((bookingId, activity) -> currentRowsByBookingId.put(
                bookingId,
                this.rowsByDoorId.get(activity.getUserObject().doorId())));
    }

    void onActivityChangeFinished(ActivityEvent event) {
        if (!(event.getActivityRef().getActivity() instanceof BookingActivity activity)) {
            return;
        }
        BookingDto original = activity.getUserObject();
        Row<?, ?, ?> currentRow = targetRow(event);

        if (!(currentRow instanceof DockDoorRow doorRow)) {
            restore(activity, original);
            return;
        }
        currentRowsByBookingId.put(activity.bookingId(), doorRow);

        if (!pendingBookings.add(activity.bookingId())) {
            restore(activity, original);
            statusSink.accept("Already saving " + activity.getName());
            return;
        }

        ScheduleBookingCommand command = new ScheduleBookingCommand(
                doorRow.doorId(),
                activity.getStartTime(),
                activity.getEndTime(),
                original.version());

        boardClient.proposeSchedule(activity.bookingId(), command).whenComplete((result, error) -> Platform.runLater(() -> {
            pendingBookings.remove(activity.bookingId());
            if (error != null) {
                restore(activity, original);
                statusSink.accept("Network error: " + error.getMessage());
                return;
            }

            ScheduleResponseReducer.Decision decision =
                    reducer.decide(result.statusCode(), result.booking(), result.problem());
            switch (decision.action()) {
                case COMMIT -> {
                    replace(activity, decision.booking());
                    statusSink.accept("Saved " + decision.booking().reference());
                }
                case REPLACE -> {
                    replace(activity, decision.booking());
                    statusSink.accept(decision.message());
                }
                case RESTORE -> {
                    restore(activity, original);
                    statusSink.accept(decision.message());
                }
            }
        }));
    }

    static Row<?, ?, ?> targetRow(ActivityEvent event) {
        return event.getNewRow() == null
                ? event.getActivityRef().getRow()
                : event.getNewRow();
    }

    private void restore(BookingActivity activity, BookingDto original) {
        renderServerState(activity, original);
    }

    private void replace(BookingActivity activity, BookingDto booking) {
        renderServerState(activity, booking);
    }

    private void renderServerState(BookingActivity activity, BookingDto booking) {
        DockDoorRow targetRow = rowsByDoorId.get(booking.doorId());
        if (targetRow == null) {
            return;
        }

        DockDoorRow currentRow = currentRowsByBookingId.get(activity.bookingId());
        if (currentRow != null) {
            currentRow.removeActivity(bookingsLayer, activity);
        }
        activity.apply(booking);
        targetRow.addActivity(bookingsLayer, activity);
        currentRowsByBookingId.put(activity.bookingId(), targetRow);
    }
}
