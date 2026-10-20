package com.themainthread.time;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

@ApplicationScoped
public class CalendarTime {
    static final List<String> ZONES = List.of("Europe/Berlin", "UTC", "America/New_York",
            "Asia/Tokyo", "Pacific/Auckland", "Pacific/Honolulu", "Asia/Kathmandu");

    private final int year;

    public CalendarTime(AdventConfig config) {
        year = config.year();
    }

    public Snapshot snapshot(String at, String zone, String mode) {
        String requestedMode = mode == null ? "explore" : mode;
        if (!List.of("explore", "replay").contains(requestedMode)) {
            throw new BadRequestException("Mode must be explore or replay.");
        }
        try {
            ZoneId zoneId = ZoneId.of(zone == null ? "Europe/Berlin" : zone);
            boolean simulated = at != null && !at.isBlank();
            Instant instant = simulated ? Instant.parse(at) : Instant.now(Clock.systemUTC());
            if (instant.isBefore(Instant.parse("1900-01-01T00:00:00Z"))
                    || instant.isAfter(Instant.parse("2100-12-31T23:59:59Z"))) {
                throw new BadRequestException("Choose an instant between 1900 and 2100.");
            }
            Clock clock = Clock.fixed(instant, zoneId);
            return new Snapshot(instant, zoneId.getId(), LocalDate.now(clock), requestedMode, simulated, year);
        } catch (DateTimeException exception) {
            throw new BadRequestException("Use an ISO instant with an offset and a valid time-zone ID.");
        }
    }

    public record Snapshot(Instant instant, String zone, LocalDate localDate, String mode,
            boolean simulated, int year) {
        public Clock clock() {
            return Clock.fixed(instant, ZoneId.of(zone));
        }

        public Instant unlockAt(int door) {
            return LocalDate.of(year, 12, door).atStartOfDay(ZoneId.of(zone)).toInstant();
        }

        public boolean available(int door) {
            return mode.equals("explore") || !instant.isBefore(unlockAt(door));
        }
    }
}
