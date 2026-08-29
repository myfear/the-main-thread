package com.themainthread.planner.desktop;

import com.flexganttfx.model.Row;

public final class DockDoorRow extends Row<DockDoorRow, DockDoorRow, BookingActivity> {

    private final String doorId;

    public DockDoorRow(String doorId, String displayName) {
        super(displayName);
        this.doorId = doorId;
    }

    public String doorId() {
        return doorId;
    }
}
