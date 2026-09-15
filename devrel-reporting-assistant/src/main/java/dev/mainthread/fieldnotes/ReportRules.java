package dev.mainthread.fieldnotes;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The same field definitions drive tool discovery, browser controls, and validation. */
public final class ReportRules {
    public record Field(String id, String label, String type, List<String> options, String reason) {}
    public record Issue(String field, String message) {}
    public record Validation(boolean ready, List<Issue> issues, List<String> required) {}

    private static final Map<String, Field> FIELDS = new LinkedHashMap<>();
    static {
        field("date", "Activity date", "date", "Date must be in the reporting month, not in the future.");
        field("author", "Advocate", "select", "Who carried out this activity?", "Taylor Quinn", "Jordan Vale", "Morgan Reed");
        field("product", "Primary product", "select", "Choose the primary area.", "Forge Runtime", "Orbit AI Toolkit", "Harbor Platform", "Community OSS");
        field("project", "Open-source project", "text", "Community OSS needs a project name.");
        field("title", "Activity title", "text", "Give the activity a short descriptive title.");
        field("type", "Activity type", "select", "The activity type determines follow-up requirements.", "talk", "workshop", "customer session", "article", "recorded video", "open-source contribution");
        field("outcome", "Outcome / description", "textarea", "Describe the observed outcome. It is fine to say the impact is not yet known.");
        field("event", "Event or session name", "text", "Live engagements need an event or session name.");
        field("delivery", "Delivery format", "select", "Attendance and travel depend on delivery format.", "in person", "online", "hybrid");
        field("onsiteAttendees", "On-site attendees", "integer", "Enter an actual count, including zero; unknown is not zero.");
        field("remoteAttendees", "Remote attendees", "integer", "Enter an actual count, including zero; unknown is not zero.");
        field("duration", "Duration in hours", "number", "Live engagements require a duration greater than zero and no more than 24 hours.");
        field("completedExercises", "Participants completing the exercise", "integer", "Workshop completions cannot exceed total attendance.");
        field("audience", "Audience", "select", "Audience determines which evidence is appropriate.", "public community", "internal team", "private customer");
        field("publicUrl", "Public resource / event URL", "url", "Provide an HTTPS reference. This demo does not fetch it.");
        field("internalUrl", "Internal evidence URL", "url", "Internal and private engagements need internal evidence.");
        field("accountAlias", "Fictional account alias", "text", "Identify the customer using a fictional alias.");
        field("shareable", "Can you reference supporting materials?", "boolean", "Tell us whether a deck or repository can be referenced.");
        field("materialsUrl", "Deck or repository URL", "url", "For private audiences this reference remains internal.");
        field("materialsReason", "Why are materials unavailable?", "text", "Explain why a supporting reference is unavailable.");
        field("travel", "Did you travel?", "boolean", "Only in-person or hybrid engagements have travel questions.");
        field("tripStatus", "Trip report status", "select", "A trip report can be linked or explicitly pending.", "linked", "pending");
        field("tripUrl", "Trip report URL", "url", "Link the completed trip report.");
        field("tripDue", "Trip report due date", "date", "Due within 14 days of the activity and not already overdue.");
        field("metrics", "Measurement strategy", "select", "Missing views and zero views are different.", "manual", "collect later");
        field("views", "Views", "integer", "Use a measured count; zero is a valid measured value.");
        field("measuredOn", "Measurement date", "date", "Between the activity date and today.");
        field("source", "Metrics source", "select", "This demo records collection intent; it does not run a collector.", "VidNest", "DevJournal");
        field("repositoryUrl", "Repository URL", "url", "Identify the repository receiving the contribution.");
        field("changeUrl", "Change / pull request URL", "url", "Provide evidence of the change.");
        field("contributionStatus", "Contribution status", "select", "Proposed work needs a follow-up; released work needs a tag.", "proposed", "merged", "released");
        field("releaseTag", "Release tag", "text", "Identify the released version.");
        field("followUp", "Is there an outstanding action?", "boolean", "Record promises and unresolved questions explicitly.");
        field("action", "Follow-up action", "text", "Describe the next action.");
        field("owner", "Follow-up owner", "select", "Who will complete the action?", "Taylor Quinn", "Jordan Vale", "Morgan Reed");
        field("due", "Follow-up due date", "date", "Due today or later.");
    }

    private static void field(String id, String label, String type, String reason, String... options) {
        FIELDS.put(id, new Field(id, label, type, List.of(options), reason));
    }

    public static List<Field> catalog() { return List.copyOf(FIELDS.values()); }
    public static Field field(String id) {
        Field field = FIELDS.get(id);
        if (field == null) throw new IllegalArgumentException("Unknown report field: " + id);
        return field;
    }

    public static Set<String> required(Map<String, Object> v) {
        Set<String> r = new LinkedHashSet<>(List.of("date", "author", "product", "title", "type", "outcome"));
        if (is(v, "product", "Community OSS")) r.add("project");
        if (live(v)) {
            r.addAll(List.of("event", "delivery", "duration", "audience"));
            if (is(v, "delivery", "in person", "hybrid")) r.addAll(List.of("onsiteAttendees", "travel"));
            if (is(v, "delivery", "online", "hybrid")) r.add("remoteAttendees");
            if (is(v, "type", "workshop")) r.add("completedExercises");
            if (is(v, "audience", "public community")) r.add("publicUrl");
            if (is(v, "audience", "internal team", "private customer")) r.add("internalUrl");
            if (is(v, "audience", "private customer")) r.add("accountAlias");
            if (is(v, "type", "talk", "workshop")) {
                r.add("shareable");
                if (Boolean.TRUE.equals(v.get("shareable"))) r.add("materialsUrl");
                if (Boolean.FALSE.equals(v.get("shareable"))) r.add("materialsReason");
            }
            if (r.contains("travel") && Boolean.TRUE.equals(v.get("travel"))) {
                r.add("tripStatus");
                if (is(v, "tripStatus", "linked")) r.add("tripUrl");
                if (is(v, "tripStatus", "pending")) r.add("tripDue");
            }
        }
        if (is(v, "type", "article", "recorded video")) {
            r.addAll(List.of("publicUrl", "metrics"));
            if (is(v, "metrics", "manual")) r.addAll(List.of("views", "measuredOn"));
            if (is(v, "metrics", "collect later")) r.add("source");
        }
        if (is(v, "type", "open-source contribution")) {
            r.addAll(List.of("repositoryUrl", "changeUrl", "contributionStatus"));
            if (is(v, "contributionStatus", "released")) r.add("releaseTag");
            if (is(v, "contributionStatus", "proposed")) r.addAll(List.of("action", "owner", "due"));
        } else if (v.containsKey("type")) {
            r.add("followUp");
            if (Boolean.TRUE.equals(v.get("followUp"))) r.addAll(List.of("action", "owner", "due"));
        }
        return r;
    }

    public static Object normalize(String id, Object value) {
        Field f = field(id);
        if (value == null || value instanceof String s && s.isBlank()) return null;
        switch (f.type()) {
            case "boolean" -> {
                if (!(value instanceof Boolean)) throw new IllegalArgumentException(f.label() + " must be yes or no");
            }
            case "number", "integer" -> {
                if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < 0
                        || n.doubleValue() > 1_000_000_000 || f.type().equals("integer") && n.doubleValue() != Math.rint(n.doubleValue())) {
                    throw new IllegalArgumentException(f.label() + " must be a nonnegative " + f.type());
                }
            }
            default -> {
                if (!(value instanceof String s) || s.length() > 4000) throw new IllegalArgumentException(f.label() + " must be text (up to 4000 characters)");
                value = s.trim();
                if (f.type().equals("select") && !f.options().contains(value)) throw new IllegalArgumentException("Unknown choice for " + f.label());
                if (f.type().equals("date")) {
                    try { LocalDate.parse(value.toString()); }
                    catch (RuntimeException e) { throw new IllegalArgumentException(f.label() + " must use YYYY-MM-DD"); }
                }
                if (f.type().equals("url")) {
                    try {
                        URI uri = URI.create(value.toString());
                        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
                    } catch (RuntimeException e) { throw new IllegalArgumentException(f.label() + " must be an HTTPS URL without credentials"); }
                }
            }
        }
        return value;
    }

    public static Validation validate(Map<String, Object> values, YearMonth month, LocalDate today) {
        Set<String> required = required(values);
        List<Issue> issues = new ArrayList<>();
        for (String id : required) {
            if (!values.containsKey(id)) issues.add(new Issue(id, field(id).reason()));
        }
        LocalDate date = date(values, "date");
        if (date != null && (!YearMonth.from(date).equals(month) || date.isAfter(today))) issues.add(new Issue("date", "Use a date in " + month + " that is not in the future."));
        if (values.get("duration") instanceof Number n && (n.doubleValue() <= 0 || n.doubleValue() > 24)) issues.add(new Issue("duration", "Duration must be greater than zero and at most 24 hours."));
        if (is(values, "type", "customer session") && values.containsKey("audience") && !is(values, "audience", "private customer")) issues.add(new Issue("audience", "Customer sessions require a private customer audience."));
        Long total = attendance(values);
        if (total != null && values.get("completedExercises") instanceof Number n && n.longValue() > total) issues.add(new Issue("completedExercises", "Completions (" + n.longValue() + ") exceed total attendance (" + total + ")."));
        LocalDate measured = date(values, "measuredOn");
        if (measured != null && (measured.isAfter(today) || date != null && measured.isBefore(date))) issues.add(new Issue("measuredOn", "Measurement must be between the activity date and today."));
        LocalDate trip = date(values, "tripDue");
        if (trip != null && (trip.isBefore(today) || date != null && (trip.isBefore(date) || trip.isAfter(date.plusDays(14))))) issues.add(new Issue("tripDue", "Trip report must be due within 14 days of the activity and not overdue."));
        LocalDate due = date(values, "due");
        if (due != null && due.isBefore(today)) issues.add(new Issue("due", "Follow-up must be due today or later."));
        if (is(values, "metrics", "collect later") && values.containsKey("source") && values.containsKey("publicUrl")) {
            String host = is(values, "source", "VidNest") ? "video.example.org" : "journal.example.org";
            if (!host.equalsIgnoreCase(URI.create(values.get("publicUrl").toString()).getHost())) issues.add(new Issue("publicUrl", "Use the fictional " + host + " source URL, or choose manual measurement."));
        }
        return new Validation(issues.isEmpty(), List.copyOf(issues), List.copyOf(required));
    }

    public static Long attendance(Map<String, Object> v) {
        if (!live(v) || !v.containsKey("delivery")) return null;
        long total = 0;
        for (String id : List.of("onsiteAttendees", "remoteAttendees")) {
            if (required(v).contains(id)) {
                if (!(v.get(id) instanceof Number n)) return null;
                total += n.longValue();
            }
        }
        return total;
    }

    private static boolean live(Map<String, Object> v) { return is(v, "type", "talk", "workshop", "customer session"); }
    private static LocalDate date(Map<String, Object> v, String id) { return v.get(id) == null ? null : LocalDate.parse(v.get(id).toString()); }
    private static boolean is(Map<String, Object> v, String id, String... options) { return v.get(id) != null && List.of(options).contains(v.get(id)); }
}
