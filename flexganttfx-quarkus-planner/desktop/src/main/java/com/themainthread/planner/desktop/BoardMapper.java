package com.themainthread.planner.desktop;

import com.themainthread.planner.contract.BoardDto;
import com.themainthread.planner.contract.BookingDto;
import com.themainthread.planner.contract.DockDoorDto;
import java.util.HashMap;
import java.util.Map;

public final class BoardMapper {

    public record MappedBoard(DockDoorRow root, Map<String, DockDoorRow> rowsByDoorId, Map<String, BookingActivity> activitiesById) {
    }

    public MappedBoard map(BoardDto board) {
        DockDoorRow root = new DockDoorRow("root", "ROOT");
        Map<String, DockDoorRow> rowsByDoorId = new HashMap<>();
        Map<String, BookingActivity> activitiesById = new HashMap<>();

        for (DockDoorDto door : board.doors()) {
            DockDoorRow row = new DockDoorRow(door.id(), door.name());
            rowsByDoorId.put(door.id(), row);
            root.getChildren().add(row);
        }

        for (BookingDto booking : board.bookings()) {
            DockDoorRow row = rowsByDoorId.get(booking.doorId());
            if (row == null) {
                throw new IllegalStateException("Unknown door for booking " + booking.reference());
            }
            BookingActivity activity = new BookingActivity(booking);
            activitiesById.put(booking.id(), activity);
        }

        return new MappedBoard(root, rowsByDoorId, activitiesById);
    }

    public void attachActivities(MappedBoard mapped, com.flexganttfx.model.Layer layer) {
        for (BookingActivity activity : mapped.activitiesById().values()) {
            DockDoorRow row = mapped.rowsByDoorId().get(activity.getUserObject().doorId());
            row.addActivity(layer, activity);
        }
    }
}
