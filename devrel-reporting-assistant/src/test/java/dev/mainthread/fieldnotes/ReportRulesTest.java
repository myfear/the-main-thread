package dev.mainthread.fieldnotes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ReportRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);
    private static final YearMonth MONTH = YearMonth.of(2026, 9);

    @Test
    void workshopCorrectionChangesReadinessWithoutInventingAttendance() {
        Map<String, Object> report = workshop();
        assertEquals(50L, ReportRules.attendance(report));
        report.put("completedExercises", 55);
        assertIssue(report, "completedExercises");
        report.put("completedExercises", 41);
        assertReady(report);
        report.remove("remoteAttendees");
        assertNull(ReportRules.attendance(report));
        assertIssue(report, "remoteAttendees");
    }

    @Test
    void zeroAttendanceAndZeroCompletionsAreValidMeasuredCounts() {
        Map<String, Object> report = workshop();
        report.put("onsiteAttendees", 0);
        report.put("remoteAttendees", 0);
        report.put("completedExercises", 0);
        assertEquals(0L, ReportRules.attendance(report));
        assertReady(report);
    }

    @Test
    void deliveryChangesRecomputeApplicableFieldsAndTotal() {
        Map<String, Object> report = workshop();
        report.put("delivery", "online");
        assertFalse(ReportRules.required(report).contains("onsiteAttendees"));
        assertFalse(ReportRules.required(report).contains("travel"));
        assertEquals(18L, ReportRules.attendance(report));
        report.put("delivery", "in person");
        assertFalse(ReportRules.required(report).contains("remoteAttendees"));
        assertTrue(ReportRules.required(report).contains("travel"));
        assertEquals(32L, ReportRules.attendance(report));
    }

    @Test
    void manualZeroViewsAreDifferentFromMissingViewsAndPendingCollection() {
        Map<String, Object> report = published("recorded video");
        report.put("metrics", "manual");
        report.put("measuredOn", "2026-09-14");
        assertIssue(report, "views");
        report.put("views", 0);
        assertReady(report);
        report.put("metrics", "collect later");
        report.remove("views");
        report.remove("measuredOn");
        report.put("source", "VidNest");
        assertReady(report);
        assertFalse(ReportRules.required(report).contains("views"));
        assertFalse(ReportRules.required(report).contains("measuredOn"));
        assertFalse(report.containsKey("views"));
    }

    @Test
    void deferredMetricsRequireTheSelectedSourceDomain() {
        Map<String, Object> report = published("article");
        report.put("metrics", "collect later");
        report.put("source", "DevJournal");
        assertIssue(report, "publicUrl");
        report.put("publicUrl", "https://journal.example.org/posts/runtime");
        assertReady(report);
        report.put("publicUrl", "https://journal.example.org.unrelated.example/posts/runtime");
        assertIssue(report, "publicUrl");
    }

    @Test
    void privateCustomerEvidenceRequiresAliasAndInternalReference() {
        Map<String, Object> report = workshop();
        report.put("type", "customer session");
        report.put("audience", "private customer");
        report.remove("publicUrl");
        report.remove("completedExercises");
        report.remove("shareable");
        report.remove("materialsUrl");
        assertIssue(report, "accountAlias");
        assertIssue(report, "internalUrl");
        assertFalse(ReportRules.required(report).contains("publicUrl"));
        report.put("accountAlias", "Acorn Labs");
        report.put("internalUrl", "https://internal.example.org/meetings/acorn");
        assertReady(report);
        report.put("audience", "public community");
        report.put("publicUrl", "https://events.example.org/session");
        assertIssue(report, "audience");
    }

    @Test
    void internalTalkAllowsAnExplicitReasonForUnavailableMaterials() {
        Map<String, Object> report = workshop();
        report.put("type", "talk");
        report.remove("completedExercises");
        report.put("audience", "internal team");
        report.remove("publicUrl");
        report.put("internalUrl", "https://internal.example.org/enablement");
        report.put("shareable", false);
        report.remove("materialsUrl");
        assertIssue(report, "materialsReason");
        report.put("materialsReason", "The session used an unpublished prototype.");
        assertReady(report);
    }

    @Test
    void activityDateMustBeWithinReportingMonthAndNotInFuture() {
        Map<String, Object> report = workshop();
        for (String date : List.of("2026-08-31", "2026-09-15", "2026-10-01")) {
            report.put("date", date);
            assertIssue(report, "date");
        }
        for (String date : List.of("2026-09-01", "2026-09-14")) {
            report.put("date", date);
            assertReady(report);
        }
    }

    @Test
    void measurementDatesIncludeActivityDateAndToday() {
        Map<String, Object> report = published("article");
        report.put("metrics", "manual");
        report.put("views", 10);
        for (String date : List.of("2026-09-09", "2026-09-15")) {
            report.put("measuredOn", date);
            assertIssue(report, "measuredOn");
        }
        for (String date : List.of("2026-09-10", "2026-09-14")) {
            report.put("measuredOn", date);
            assertReady(report);
        }
    }

    @Test
    void tripReportCanBeLinkedOrPendingWithinItsDueWindow() {
        Map<String, Object> report = workshop();
        report.put("travel", true);
        report.put("tripStatus", "pending");
        assertIssue(report, "tripDue");
        for (String date : List.of("2026-09-13", "2026-09-25")) {
            report.put("tripDue", date);
            assertIssue(report, "tripDue");
        }
        for (String date : List.of("2026-09-14", "2026-09-24")) {
            report.put("tripDue", date);
            assertReady(report);
        }
        report.remove("tripDue");
        report.put("tripStatus", "linked");
        assertIssue(report, "tripUrl");
        report.put("tripUrl", "https://internal.example.org/trips/runtime-days");
        assertReady(report);
    }

    @Test
    void liveDurationMustBePositiveAndAtMostTwentyFourHours() {
        Map<String, Object> report = workshop();
        for (Number duration : List.of(0, 24.01)) {
            report.put("duration", duration);
            assertIssue(report, "duration");
        }
        for (Number duration : List.of(0.25, 24)) {
            report.put("duration", duration);
            assertReady(report);
        }
    }

    @Test
    void proposedContributionNeedsFollowUpAndReleasedContributionNeedsTag() {
        Map<String, Object> report = base("open-source contribution");
        report.put("product", "Community OSS");
        report.put("repositoryUrl", "https://code.example.org/community/runtime");
        report.put("changeUrl", "https://code.example.org/community/runtime/pull/17");
        report.put("contributionStatus", "proposed");
        assertIssue(report, "project");
        report.put("project", "Community Runtime");
        for (String field : List.of("action", "owner", "due")) assertIssue(report, field);
        report.put("action", "Respond to maintainer review.");
        report.put("owner", "Jordan Vale");
        report.put("due", "2026-09-13");
        assertIssue(report, "due");
        report.put("due", "2026-09-14");
        assertReady(report);
        report.put("contributionStatus", "merged");
        report.remove("action");
        report.remove("owner");
        report.remove("due");
        assertReady(report);
        report.put("contributionStatus", "released");
        assertIssue(report, "releaseTag");
        report.put("releaseTag", "v1.2.0");
        assertReady(report);
    }

    @Test
    void outstandingActionRequiresOwnerAndNonOverdueDate() {
        Map<String, Object> report = workshop();
        report.put("followUp", true);
        for (String field : List.of("action", "owner", "due")) assertIssue(report, field);
        report.put("action", "Send the promised sample project.");
        report.put("owner", "Taylor Quinn");
        report.put("due", "2026-09-13");
        assertIssue(report, "due");
        report.put("due", "2026-09-17");
        assertReady(report);
    }

    @Test
    void normalizeRejectsMalformedToolValuesBeforeDraftValidation() {
        assertEquals("A title", ReportRules.normalize("title", "  A title  "));
        assertNull(ReportRules.normalize("title", "  "));
        assertEquals(false, ReportRules.normalize("travel", false));
        assertEquals(0, ReportRules.normalize("views", 0));
        for (Object count : List.of(-1, 1.5, "12", Double.NaN, Double.POSITIVE_INFINITY, 1_000_000_001L)) {
            assertThrows(IllegalArgumentException.class, () -> ReportRules.normalize("views", count));
        }
        assertThrows(IllegalArgumentException.class, () -> ReportRules.normalize("travel", "yes"));
        assertThrows(IllegalArgumentException.class, () -> ReportRules.normalize("author", "Unknown Person"));
        assertThrows(IllegalArgumentException.class, () -> ReportRules.normalize("date", "14/09/2026"));
        assertThrows(IllegalArgumentException.class, () -> ReportRules.normalize("ready", true));
        for (String url : List.of("http://example.org", "https://@example.org", "javascript:alert(1)", "/relative")) {
            assertThrows(IllegalArgumentException.class, () -> ReportRules.normalize("publicUrl", url));
        }
    }

    private static Map<String, Object> base(String type) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("date", "2026-09-10");
        report.put("author", "Taylor Quinn");
        report.put("product", "Orbit AI Toolkit");
        report.put("title", "Runtime Days example project");
        report.put("type", type);
        report.put("outcome", "Participants tried the example; longer-term impact is not yet known.");
        return report;
    }

    private static Map<String, Object> workshop() {
        Map<String, Object> report = base("workshop");
        report.put("event", "Runtime Days");
        report.put("delivery", "hybrid");
        report.put("duration", 2);
        report.put("onsiteAttendees", 32);
        report.put("remoteAttendees", 18);
        report.put("completedExercises", 41);
        report.put("audience", "public community");
        report.put("publicUrl", "https://events.example.org/runtime-days");
        report.put("shareable", true);
        report.put("materialsUrl", "https://code.example.org/demo/lab");
        report.put("travel", false);
        report.put("followUp", false);
        return report;
    }

    private static Map<String, Object> published(String type) {
        Map<String, Object> report = base(type);
        report.put("publicUrl", "https://video.example.org/watch/runtime");
        report.put("followUp", false);
        return report;
    }

    private static void assertReady(Map<String, Object> report) {
        ReportRules.Validation validation = ReportRules.validate(report, MONTH, TODAY);
        assertTrue(validation.ready(), () -> validation.issues().toString());
    }

    private static void assertIssue(Map<String, Object> report, String field) {
        ReportRules.Validation validation = ReportRules.validate(report, MONTH, TODAY);
        assertFalse(validation.ready());
        assertTrue(validation.issues().stream().anyMatch(issue -> issue.field().equals(field)),
                () -> "Expected issue for " + field + ", got " + validation.issues());
    }
}
