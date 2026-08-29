package com.themainthread.planner.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.themainthread.planner.contract.BoardDto;
import com.themainthread.planner.contract.BookingDto;
import com.themainthread.planner.contract.DockDoorDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoardMapperTest {

    @Test
    void mapsDoorsAndBookingsIntoRowsAndActivities() {
        BoardDto board = new BoardDto(
                List.of(new DockDoorDto("door-3", "Door 3"), new DockDoorDto("door-5", "Door 5")),
                List.of(new BookingDto(
                        "booking-42",
                        "TRUCK-1042",
                        "door-3",
                        Instant.parse("2026-08-20T08:00:00Z"),
                        Instant.parse("2026-08-20T09:30:00Z"),
                        1)));

        BoardMapper.MappedBoard mapped = new BoardMapper().map(board);

        assertEquals(2, mapped.root().getChildren().size());
        assertEquals("TRUCK-1042", mapped.activitiesById().get("booking-42").getName());
        assertEquals("door-3", mapped.rowsByDoorId().get("door-3").doorId());
    }
}
