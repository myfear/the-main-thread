package com.themainthread.shapeshift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

class CardParserTest {
    private final CardParser parser = new CardParser(
            Clock.fixed(Instant.parse("2026-10-13T09:00:00Z"), ZoneId.of("Europe/Berlin")));

    @Test
    void parsesCalendarFieldsWithoutModelExtraction() {
        var card = parser.parse(Intent.EVENT, "Dinner with Priya tomorrow at 8pm");
        assertEquals("2026-10-14", card.fields().get("date"));
        assertEquals("20:00", card.fields().get("time"));
        assertTrue(card.warnings().isEmpty());
        assertEquals("2026-10-16", parser.parse(Intent.EVENT, "Lunch Friday at 12pm").fields().get("date"));
    }

    @Test
    void requiresConfirmationForMissingAndInvalidDates() {
        assertFalse(parser.parse(Intent.REMINDER, "Remind me to pay rent").warnings().isEmpty());
        assertFalse(parser.parse(Intent.EVENT, "Meet 2026-02-30 at 25:00").warnings().isEmpty());
    }

    @Test
    void separatesChecklistItems() {
        assertEquals(List.of("milk", "eggs", "bread", "coffee"),
                parser.parse(Intent.CHECKLIST, "Buy milk, eggs, bread and coffee").fields().get("items"));
    }

    @Test
    void combinesDurationUnitsAndRejectsUnusableDurations() {
        assertEquals(3900L, parser.parse(Intent.TIMER, "1 hour 5 minutes focus").fields().get("seconds"));
        assertEquals(0L, parser.parse(Intent.TIMER, "0 min timer").fields().get("seconds"));
        assertEquals(0L, parser.parse(Intent.TIMER, "48 hours timer").fields().get("seconds"));
        assertEquals(0L, parser.parse(Intent.TIMER, "1.5 hours timer").fields().get("seconds"));
        assertEquals(0L, parser.parse(Intent.TIMER, "-5 minutes timer").fields().get("seconds"));
    }

    @Test
    void balancesMoneyToTheCent() {
        var fields = parser.parse(Intent.SPLIT, "Split 10 between 3").fields();
        assertEquals("3.33", fields.get("perPerson"));
        assertEquals(1L, fields.get("remainderCents"));
        assertFalse(parser.parse(Intent.SPLIT, "split 100 between 0").warnings().isEmpty());
        assertFalse(parser.parse(Intent.SPLIT, "split 100 between 3.5").warnings().isEmpty());
    }

    @Test
    void preservesNotesAsText() {
        assertEquals("<script>alert(1)</script>", parser.parse(Intent.NOTE, "<script>alert(1)</script>").fields().get("title"));
    }
}
