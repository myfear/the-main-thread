package com.themainthread.planner;

import com.themainthread.planner.contract.BookingDto;
import com.themainthread.planner.contract.DockDoorDto;

final class PlannerMapper {

    private PlannerMapper() {
    }

    static DockDoorDto toDto(DockDoorEntity door) {
        return new DockDoorDto(door.id, door.name);
    }

    static BookingDto toDto(BookingEntity booking) {
        return new BookingDto(
                booking.id,
                booking.reference,
                booking.door.id,
                booking.startsAt,
                booking.endsAt,
                booking.version);
    }
}
