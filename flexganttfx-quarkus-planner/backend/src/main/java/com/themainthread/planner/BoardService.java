package com.themainthread.planner;

import com.themainthread.planner.contract.BoardDto;
import com.themainthread.planner.contract.BookingDto;
import com.themainthread.planner.contract.DockDoorDto;
import com.themainthread.planner.contract.ScheduleBookingCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class BoardService {

    private static final Logger LOG = Logger.getLogger(BoardService.class);
    private static final Duration MAX_WINDOW = Duration.ofDays(7);

    public BoardDto loadBoard(Instant from, Instant to) {
        validateWindow(from, to);

        List<DockDoorDto> doors = DockDoorEntity.<DockDoorEntity>listAll().stream()
                .map(PlannerMapper::toDto)
                .toList();

        List<BookingDto> bookings = BookingEntity.find(
                        "startsAt < ?1 and endsAt > ?2",
                        to,
                        from)
                .<BookingEntity>list()
                .stream()
                .map(PlannerMapper::toDto)
                .toList();

        return new BoardDto(doors, bookings);
    }

    @Transactional
    public BookingDto schedule(String bookingId, ScheduleBookingCommand command) {
        BookingEntity booking = BookingEntity.findById(bookingId);
        if (booking == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        if (booking.version != command.expectedVersion()) {
            throw ScheduleConflictException.stale(PlannerMapper.toDto(booking));
        }

        if (!command.startsAt().isBefore(command.endsAt())) {
            throw new WebApplicationException("startsAt must be before endsAt", Response.Status.BAD_REQUEST);
        }

        DockDoorEntity door = DockDoorEntity.findById(command.doorId());
        if (door == null) {
            throw new WebApplicationException("Unknown door: " + command.doorId(), Response.Status.BAD_REQUEST);
        }

        BookingEntity overlap = BookingEntity.find(
                        "door = ?1 and id <> ?2 and startsAt < ?3 and endsAt > ?4",
                        door,
                        bookingId,
                        command.endsAt(),
                        command.startsAt())
                .firstResult();
        if (overlap != null) {
            throw ScheduleConflictException.overlapping(overlap.reference);
        }

        booking.door = door;
        booking.startsAt = command.startsAt();
        booking.endsAt = command.endsAt();
        BookingEntity.getEntityManager().flush();

        LOG.debugf(
                "Accepted schedule proposal for %s on %s",
                booking.reference,
                door.name);
        return PlannerMapper.toDto(booking);
    }

    private static void validateWindow(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new WebApplicationException("from must be before to", Response.Status.BAD_REQUEST);
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new WebApplicationException(
                    "Requested window exceeds seven days",
                    Response.Status.BAD_REQUEST);
        }
    }
}
