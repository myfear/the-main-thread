package com.themainthread.shapeshift;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CardParser {
    private static final Pattern DURATION = Pattern
            .compile("(?<![\\w.\\-])(\\d+)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPLIT = Pattern.compile(
            "(?:split|divide|share)\\s*[$€£]?\\s*(\\d+(?:\\.\\d{1,2})?)(?:\\s*(?:euros?|dollars?|pounds?|EUR|USD|GBP))?\\s*(?:between|among|by|/)\\s*(\\d+)(?![\\w.])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile(
            "\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b|\\bat\\s+(\\d{1,2}):(\\d{2})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private final Clock clock;

    public record Card(String type, Map<String, Object> fields, List<String> warnings) {
    }

    @Inject
    public CardParser(ShapeConfig config) {
        this(Clock.system(ZoneId.of(config.zone())));
    }

    CardParser(Clock clock) {
        this.clock = clock;
    }

    Card parse(Intent intent, String text) {
        var warnings = new ArrayList<String>();
        Map<String, Object> fields = switch (intent) {
            case EVENT, REMINDER -> schedule(text, warnings);
            case CHECKLIST -> checklist(text, warnings);
            case TIMER -> timer(text, warnings);
            case SPLIT -> split(text, warnings);
            case NOTE, NONE -> Map.of("title", text);
        };
        return new Card(intent.name(), fields, warnings);
    }

    private Map<String, Object> checklist(String text, List<String> warnings) {
        String list = text.replaceFirst("(?i)^(buy|shopping list:?|checklist:?|todo:?|to do:?|tasks:?)\\s*", "");
        var items = Arrays.stream(list.split("\\s*[,;\\n]\\s*|\\s+and\\s+"))
                .filter(item -> !item.isBlank()).toList();
        if (items.size() < 2) {
            warnings.add("Only one item found. Edit the checklist if needed.");
        }
        return Map.of("items", items);
    }

    private Map<String, Object> timer(String text, List<String> warnings) {
        long seconds = 0;
        var matcher = DURATION.matcher(text);
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            seconds = Math.addExact(seconds,
                    Math.multiplyExact(value, unit.startsWith("h") ? 3600 : unit.startsWith("m") ? 60 : 1));
        }
        if (seconds < 1 || seconds > 86400) {
            warnings.add("Enter a duration from 1 second to 24 hours.");
            seconds = 0;
        }
        return Map.of("title", text, "seconds", seconds);
    }

    private Map<String, Object> split(String text, List<String> warnings) {
        var matcher = SPLIT.matcher(text);
        if (!matcher.find()) {
            warnings.add("Use a decimal point and a numeric count: split 120.50 between 3.");
            return Map.of("total", "", "people", 0, "perPerson", "", "remainderCents", 0);
        }
        var total = new BigDecimal(matcher.group(1));
        int people = Integer.parseInt(matcher.group(2));
        if (people < 1 || people > 1000 || total.compareTo(new BigDecimal("1000000")) > 0) {
            warnings.add("Use 1 to 1000 people and an amount no greater than 1,000,000.");
            return Map.of("total", total.toPlainString(), "people", 0, "perPerson", "", "remainderCents", 0);
        }
        long cents = total.movePointRight(2).longValueExact();
        return Map.of("total", total.setScale(2).toPlainString(), "people", people,
                "perPerson", BigDecimal.valueOf(cents / people, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                "remainderCents", cents % people);
    }

    private Map<String, Object> schedule(String text, List<String> warnings) {
        String lower = text.toLowerCase(Locale.ROOT);
        LocalDate day = null;
        var iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            try {
                day = LocalDate.parse(iso.group());
            } catch (RuntimeException e) {
                warnings.add("The calendar date is invalid.");
            }
        } else if (lower.matches(".*\\btomorrow\\b.*")) {
            day = LocalDate.now(clock).plusDays(1);
        } else if (lower.matches(".*\\btoday\\b.*")) {
            day = LocalDate.now(clock);
        } else {
            for (DayOfWeek weekday : DayOfWeek.values()) {
                if (lower.contains(weekday.name().toLowerCase(Locale.ROOT))) {
                    day = LocalDate.now(clock).with(TemporalAdjusters.next(weekday));
                    break;
                }
            }
        }
        LocalTime time = null;
        var match = TIME.matcher(text);
        if (match.find()) {
            try {
                int hour = Integer.parseInt(match.group(1) == null ? match.group(4) : match.group(1));
                int minute = match.group(1) == null ? Integer.parseInt(match.group(5))
                        : match.group(2) == null ? 0 : Integer.parseInt(match.group(2));
                if (match.group(3) != null) {
                    if (hour < 1 || hour > 12) {
                        throw new IllegalArgumentException();
                    }
                    hour = hour % 12 + (match.group(3).equalsIgnoreCase("pm") ? 12 : 0);
                }
                time = LocalTime.of(hour, minute);
            } catch (RuntimeException e) {
                warnings.add("The time is invalid.");
            }
        }
        if (day == null || time == null) {
            warnings.add("Confirm date and time. Supported: tomorrow, weekdays, YYYY-MM-DD; 8pm or at 20:00.");
        }
        return Map.of("title", text, "date", day == null ? "" : day.toString(), "time", time == null ? "" : time.toString(),
                "zone", clock.getZone().getId());
    }
}
