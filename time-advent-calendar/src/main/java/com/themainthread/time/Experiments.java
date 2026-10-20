package com.themainthread.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAdjusters;
import java.time.zone.ZoneRulesProvider;
import java.util.Locale;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Experiments {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private final DatabaseExperiments database;

    public Experiments(DatabaseExperiments database) {
        this.database = database;
    }

    public ExperimentResult run(int door, String variant, CalendarTime.Snapshot time) {
        boolean alternative = variant.equals("alternative");
        return switch (door) {
            case 1 -> birthday(alternative);
            case 2 -> appointment(alternative);
            case 3 -> offset(alternative);
            case 4 -> defaults(time);
            case 5 -> conversion(alternative);
            case 6 -> equality(alternative);
            case 7 -> gap(alternative);
            case 8 -> overlap(alternative);
            case 9 -> tomorrow(alternative);
            case 10 -> midnight(alternative);
            case 11 -> months(alternative);
            case 12 -> anniversary(alternative);
            case 13 -> weekYear(alternative);
            case 14 -> parsing(alternative);
            case 15 -> locale(alternative);
            case 16 -> database.precision(alternative);
            case 17 -> boundaries(alternative);
            case 18 -> businessDays(alternative);
            case 19 -> futureRules(alternative);
            case 20 -> testing(time);
            case 21 -> elapsed(alternative);
            case 22 -> database.zone(alternative);
            case 23 -> ordering(alternative);
            case 24 -> database.recovery(alternative, time);
            default -> throw new IllegalArgumentException("Unknown door");
        };
    }

    private ExperimentResult birthday(boolean alternative) {
        LocalDate date = LocalDate.of(2026, 12, 24);
        ZoneId viewer = ZoneId.of(alternative ? "America/Los_Angeles" : "Pacific/Honolulu");
        Instant inventedMidnight = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        return ExperimentResult.of("Keep a date-only promise as LocalDate. Introducing a midnight instant changes its meaning.",
                "Date-only value", date, "Invented midnight UTC", inventedMidnight, "Viewer zone", viewer,
                "Date after converting that instant", inventedMidnight.atZone(viewer).toLocalDate());
    }

    private ExperimentResult appointment(boolean alternative) {
        LocalDateTime local = LocalDateTime.of(2026, 12, 24, alternative ? 18 : 9, 0);
        return ExperimentResult.of("A LocalDateTime needs a zone or offset before it identifies an instant.",
                "Appointment on the wall clock", local, "Resolved in Berlin", local.atZone(BERLIN).toInstant(),
                "Resolved in Tokyo", local.atZone(ZoneId.of("Asia/Tokyo")).toInstant());
    }

    private ExperimentResult offset(boolean alternative) {
        LocalDateTime local = LocalDateTime.of(2026, alternative ? 7 : 12, 24, 9, 0);
        return ExperimentResult.of("A region supplies changing rules. A fixed offset always keeps the same distance from UTC.",
                "Local appointment", local, "Europe/Berlin", local.atZone(BERLIN),
                "Fixed +01:00", local.atOffset(ZoneOffset.ofHours(1)),
                "Same instant", local.atZone(BERLIN).toInstant().equals(local.toInstant(ZoneOffset.ofHours(1))));
    }

    private ExperimentResult defaults(CalendarTime.Snapshot time) {
        return ExperimentResult.of(
                "Specify the business zone. These two clocks simulate different deployment defaults without changing the JVM.",
                "One captured instant", time.instant(), "UTC deployment date",
                time.instant().atZone(ZoneOffset.UTC).toLocalDate(),
                "Berlin deployment date", time.instant().atZone(BERLIN).toLocalDate(),
                "Your chosen calendar date", time.localDate(), "Actual JVM default zone", ZoneId.systemDefault());
    }

    private ExperimentResult conversion(boolean alternative) {
        ZonedDateTime original = LocalDate.of(2026, 12, 24).atTime(9, 0).atZone(BERLIN);
        ZoneId destination = ZoneId.of(alternative ? "Asia/Tokyo" : "America/New_York");
        return ExperimentResult.of(
                "Preserve the instant when displaying an event elsewhere. Preserving the local reading reschedules the event.",
                "Original appointment", original, "Same instant", original.withZoneSameInstant(destination),
                "Same local reading", original.withZoneSameLocal(destination),
                "Rescheduled instant", original.withZoneSameLocal(destination).toInstant());
    }

    private ExperimentResult equality(boolean alternative) {
        ZonedDateTime original = Instant.parse("2026-12-24T08:00:00Z").atZone(BERLIN);
        ZonedDateTime other = original.withZoneSameInstant(ZoneId.of(alternative ? "UTC" : "Asia/Tokyo"));
        return ExperimentResult.of("equals compares the full zoned representation. isEqual compares the instant.",
                "First value", original, "Second value", other, "equals", original.equals(other),
                "isEqual", original.isEqual(other), "Same Instant", original.toInstant().equals(other.toInstant()));
    }

    private ExperimentResult gap(boolean alternative) {
        ZoneId zone = ZoneId.of(alternative ? "America/New_York" : "Europe/Berlin");
        LocalDateTime local = LocalDateTime.parse(alternative ? "2026-03-08T02:30:00" : "2026-03-29T02:30:00");
        var offsets = zone.getRules().getValidOffsets(local);
        var transition = zone.getRules().getTransition(local);
        return ExperimentResult.of(
                "Zero valid offsets means this local time does not exist. This lab's booking policy rejects it; atZone instead shifts it forward.",
                "Requested local time", local, "Zone", zone, "Valid offsets", offsets,
                "Gap length", transition.getDuration(), "Java atZone result", local.atZone(zone),
                "Explicit booking policy", offsets.isEmpty() ? "REJECT: choose an existing local time" : "ACCEPT");
    }

    private ExperimentResult overlap(boolean alternative) {
        ZoneId zone = ZoneId.of(alternative ? "America/New_York" : "Europe/Berlin");
        LocalDateTime local = LocalDateTime.parse(alternative ? "2026-11-01T01:30:00" : "2026-10-25T02:30:00");
        var offsets = zone.getRules().getValidOffsets(local);
        ZonedDateTime earlier = local.atZone(zone).withEarlierOffsetAtOverlap();
        ZonedDateTime later = local.atZone(zone).withLaterOffsetAtOverlap();
        return ExperimentResult.of(
                "Two valid offsets means two possible instants. Ask which occurrence is intended and keep that choice.",
                "Requested local time", local, "Zone", zone, "Valid offsets", offsets,
                "Earlier occurrence", earlier, "Earlier instant", earlier.toInstant(),
                "Later occurrence", later, "Later instant", later.toInstant(),
                "Distance between occurrences", Duration.between(earlier.toInstant(), later.toInstant()));
    }

    private ExperimentResult tomorrow(boolean alternative) {
        ZonedDateTime start = LocalDateTime.parse(alternative ? "2026-10-24T12:00:00" : "2026-03-28T12:00:00")
                .atZone(BERLIN);
        ZonedDateTime calendarDay = start.plus(Period.ofDays(1));
        ZonedDateTime elapsedDay = start.plus(Duration.ofDays(1));
        return ExperimentResult.of("A calendar day preserves the local schedule. A 24-hour Duration preserves elapsed time.",
                "Start", start, "Plus Period.ofDays(1)", calendarDay, "Plus Duration.ofDays(1)", elapsedDay,
                "Elapsed hours for calendar day", Duration.between(start, calendarDay).toHours(),
                "Elapsed hours for duration", Duration.between(start, elapsedDay).toHours());
    }

    private ExperimentResult midnight(boolean alternative) {
        LocalDate date = LocalDate.parse(alternative ? "2011-12-30" : "2018-11-04");
        ZoneId zone = ZoneId.of(alternative ? "Pacific/Apia" : "America/Sao_Paulo");
        ZonedDateTime start = date.atStartOfDay(zone);
        return ExperimentResult.of(
                "atStartOfDay(zone) returns the earliest valid time. Historical rules can skip midnight or even an entire local date.",
                "Requested date", date, "Zone", zone, "Earliest valid start", start,
                "Requested date survived", date.equals(start.toLocalDate()));
    }

    private ExperimentResult months(boolean alternative) {
        LocalDate start = LocalDate.of(alternative ? 2024 : 2026, 1, 31);
        LocalDate first = start.plusMonths(1);
        return ExperimentResult.of(
                "Clamping to February changes the next starting point. Calculate recurrences from an anchor or use an explicit month-end rule.",
                "Anchor date", start, "First occurrence", first, "Repeated plusMonths", first.plusMonths(1),
                "Two months from anchor", start.plusMonths(2),
                "Explicit month-end", first.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth()));
    }

    private ExperimentResult anniversary(boolean alternative) {
        LocalDate birthday = LocalDate.of(2024, 2, 29);
        int year = alternative ? 2028 : 2025;
        LocalDate februaryPolicy = MonthDay.from(birthday).atYear(year);
        LocalDate marchPolicy = LocalDate.of(year, 2, 1).isLeapYear() ? februaryPolicy : LocalDate.of(year, 3, 1);
        return ExperimentResult.of(
                "Java supplies adjustment behavior. The anniversary policy belongs to the application; preserve the original month and day.",
                "Original anniversary", birthday, "Target year", year, "MonthDay.atYear", februaryPolicy,
                "Explicit March policy", marchPolicy);
    }

    private ExperimentResult weekYear(boolean alternative) {
        LocalDate date = LocalDate.parse(alternative ? "2021-01-01" : "2019-12-30");
        return ExperimentResult.of(
                "Y is the week-based year, u is the proleptic year, and y is year-of-era. Use ISO_LOCAL_DATE for this date contract.",
                "Input date", date, "Locale", Locale.UK.toLanguageTag(),
                "YYYY-MM-dd", date.format(DateTimeFormatter.ofPattern("YYYY-MM-dd", Locale.UK)),
                "uuuu-MM-dd", date.format(DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.UK)),
                "ISO week date", date.format(DateTimeFormatter.ISO_WEEK_DATE));
    }

    private ExperimentResult parsing(boolean alternative) {
        String input = alternative ? "2026-04-31" : "2026-02-30";
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT);
        String strict;
        try {
            strict = LocalDate.parse(input, pattern.withResolverStyle(ResolverStyle.STRICT)).toString();
        } catch (DateTimeParseException exception) {
            strict = "REJECTED: date does not exist";
        }
        return ExperimentResult.of(
                "SMART resolution can adjust an invalid day. Use STRICT resolution when the input must describe an existing date.",
                "Input", input, "SMART result", LocalDate.parse(input, pattern.withResolverStyle(ResolverStyle.SMART)),
                "STRICT result", strict);
    }

    private ExperimentResult locale(boolean alternative) {
        String input = alternative ? "03/04/2026" : "12/11/2026";
        LocalDate us = LocalDate.parse(input, DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.US));
        LocalDate uk = LocalDate.parse(input, DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.UK));
        return ExperimentResult.of(
                "Numeric order is part of the contract. Locale alone does not rewrite an explicit pattern. Exchange ISO dates; localize their display.",
                "Ambiguous text", input, "MM/dd/uuuu", us, "dd/MM/uuuu", uk,
                "ISO representation of first interpretation", us.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    private ExperimentResult boundaries(boolean alternative) {
        LocalDate date = LocalDate.parse(alternative ? "2026-10-25" : "2026-03-29");
        Instant start = date.atStartOfDay(BERLIN).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(BERLIN).toInstant();
        return ExperimentResult.of(
                "Use start <= event && event < nextStart. Resolve both dates in the business zone instead of adding 24 hours.",
                "Business date", date, "Inclusive start", start, "Exclusive end", end,
                "Window hours", Duration.between(start, end).toHours(),
                "Last nanosecond included", within(end.minusNanos(1), start, end),
                "Next day's start included", within(end, start, end));
    }

    static boolean within(Instant event, Instant start, Instant end) {
        return !event.isBefore(start) && event.isBefore(end);
    }

    private ExperimentResult businessDays(boolean alternative) {
        LocalDate start = LocalDate.of(2026, 12, alternative ? 24 : 23);
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 26));
        return ExperimentResult.of(
                "This fixture excludes weekends and December 25–26 only. A real business calendar needs an owned, jurisdiction-specific holiday policy.",
                "Start (excluded from count)", start, "Three weekdays later", addBusinessDays(start, Set.of()),
                "Fixture holidays", "2026-12-25, 2026-12-26", "Three fixture business days later",
                addBusinessDays(start, holidays));
    }

    private LocalDate addBusinessDays(LocalDate start, Set<LocalDate> holidays) {
        LocalDate current = start;
        int remaining = 3;
        while (remaining > 0) {
            current = current.plusDays(1);
            if (current.getDayOfWeek().getValue() <= 5 && !holidays.contains(current)) {
                remaining--;
            }
        }
        return current;
    }

    private ExperimentResult futureRules(boolean alternative) {
        LocalDateTime appointment = LocalDateTime.of(2030, 12, 24, 9, 0);
        ZoneOffset current = BERLIN.getRules().getOffset(appointment);
        ZoneOffset hypothetical = ZoneOffset.ofTotalSeconds(current.getTotalSeconds() + (alternative ? 1800 : 3600));
        Instant stored = appointment.toInstant(current);
        return ExperimentResult.of(
                "The proposed rule is synthetic, not a prediction for Berlin. Keep local intent and region when the promise is a future local appointment, then define a reconciliation policy.",
                "Intended appointment", appointment + " Europe/Berlin",
                "Installed TZDB version", ZoneRulesProvider.getVersions("Europe/Berlin").lastKey(),
                "Currently resolved instant", stored, "Hypothetical new offset", hypothetical,
                "Preserve local appointment", appointment.toInstant(hypothetical),
                "Preserve old instant: new wall time", stored.atOffset(hypothetical).toLocalDateTime());
    }

    private ExperimentResult testing(CalendarTime.Snapshot time) {
        Instant unlockAt = time.unlockAt(20);
        Clock before = Clock.fixed(unlockAt.minusNanos(1), ZoneId.of(time.zone()));
        Clock exact = Clock.fixed(unlockAt, ZoneId.of(time.zone()));
        Clock after = Clock.fixed(unlockAt.plusNanos(1), ZoneId.of(time.zone()));
        return ExperimentResult.of(
                "Read now from an injected Clock and capture it once for the decision. The exact boundary is included. No sleeps are needed.",
                "Your request clock", Instant.now(time.clock()), "Door 20 unlock instant", unlockAt,
                "One nanosecond before", isDue(before, unlockAt), "Exactly at unlock", isDue(exact, unlockAt),
                "One nanosecond after", isDue(after, unlockAt), "Due at your chosen instant", isDue(time.clock(), unlockAt));
    }

    public static boolean isDue(Clock clock, Instant dueAt) {
        Instant now = clock.instant();
        return !now.isBefore(dueAt);
    }

    private ExperimentResult elapsed(boolean alternative) {
        Instant wallStart = Instant.parse("2026-12-24T12:00:00Z");
        Instant adjustedWallEnd = wallStart.minusSeconds(alternative ? 30 : 2);
        long start = System.nanoTime();
        long elapsedNanos = System.nanoTime() - start;
        return ExperimentResult.of(
                "The backwards wall-clock step is simulated. nanoTime measures elapsed time within one JVM; its absolute value is not a timestamp or a cross-machine clock.",
                "Simulated wall-clock start", wallStart, "Simulated adjusted end", adjustedWallEnd,
                "Misleading wall-clock duration", Duration.between(wallStart, adjustedWallEnd),
                "Actual two-read nanoTime difference (ns)", elapsedNanos);
    }

    private ExperimentResult ordering(boolean alternative) {
        Instant base = Instant.parse("2026-12-24T12:00:00Z");
        Instant request = base.plusSeconds(alternative ? 30 : 5);
        Instant reply = base.plusSeconds(1);
        return ExperimentResult.of(
                "This is a two-machine clock-skew simulation. Correlation and logical ordering express request → reply; wall-clock sorting cannot prove causality.",
                "Machine A request timestamp", request, "Machine B reply timestamp", reply,
                "Timestamp sort", reply.isBefore(request) ? "reply, request" : "request, reply",
                "Known causal order", "request → reply", "Lamport counter example", "request: 1, receive/reply: 2");
    }
}
