package com.themainthread.planner.contract;

import java.util.List;

public record BoardDto(List<DockDoorDto> doors, List<BookingDto> bookings) {
}
