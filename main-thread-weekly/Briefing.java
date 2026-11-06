//JAVA 25
//DEPS io.smallrye.ai:acp-java-core:0.2.2
//DEPS org.jsoup:jsoup:1.21.2
//SOURCES Feed.java Bob.java Site.java Checks.java

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jboss.logging.Logger;

/** A small, bounded feed -> records -> ACP -> validated issue -> static Queue pipeline. */
public class Briefing {
    static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(SerializationFeature.INDENT_OUTPUT);
    static final String VERSION = "1";
    static final Logger LOG = Logger.getLogger(Briefing.class);
    static final Set<String> TOPICS = Set.of("Java", "Quarkus", "AI", "Architecture", "Data", "Testing",
            "Developer tools", "Web", "Integration", "Security", "Observability");
    static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Berlin");

    public static void main(String[] args) {
        try {
            if (args.length == 0 || args[0].equals("help")) {
                System.out.println("Briefing: collect | build | render | test. See README.md for options.");
                return;
            }
            Map<String, String> options = options(args);
            switch (args[0]) {
                case "collect" -> collect(options, Clock.systemUTC());
                case "build" -> build(options, Clock.systemUTC());
                case "render" -> Site.render(Path.of(value(options, "state", "state")),
                        Path.of(value(options, "site", "site")), Path.of(value(options, "assets", "assets")));
                case "test" -> Checks.run();
                default -> throw new IllegalArgumentException("Unknown command; use help.");
            }
        } catch (Exception e) {
            // Never print third-party exception messages, prompts, agent chunks, or credentials.
            System.err.println("Briefing failed (" + e.getClass().getSimpleName()
                    + "). Previous published output is unchanged. Check inputs, Bob authentication, and README troubleshooting.");
            if (e instanceof UserError) System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    static Map<String, String> options(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new UserError("Expected --option value.");
            String key = args[i].substring(2);
            if (result.containsKey(key)) throw new UserError("Repeated option.");
            result.put(key, i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true");
        }
        Set<String> allowed = Set.of("state", "site", "assets", "feed", "feed-file", "week", "timezone",
                "key-file", "model", "bob", "accept-license", "force", "catalog", "catalog-file", "override");
        if (!allowed.containsAll(result.keySet())) throw new UserError("Unknown option; see README.");
        return result;
    }

    static String value(Map<String, String> options, String key, String fallback) {
        return options.getOrDefault(key, fallback);
    }

    static void collect(Map<String, String> o, Clock clock) throws Exception {
        Path state = Path.of(value(o, "state", "state"));
        String url = value(o, "feed", "https://www.the-main-thread.com/feed");
        byte[] xml = o.containsKey("feed-file") ? Files.readAllBytes(Path.of(o.get("feed-file"))) : Feed.fetch(url);
        List<ObjectNode> incoming = Feed.parse(xml, url, clock.instant());
        if (incoming.isEmpty()) throw new UserError("The feed has no eligible entries; collection was not saved.");
        ObjectNode ledger = readObject(state.resolve("records.json"));
        ledger.put("schemaVersion", 1);
        Map<String, ObjectNode> records = new HashMap<>();
        ledger.withArray("items").forEach(n -> records.put(n.path("id").asText(), (ObjectNode) n));
        for (ObjectNode item : incoming) {
            ObjectNode old = records.get(item.path("id").asText());
            item.put("firstSeen", old == null ? clock.instant().toString() : old.path("firstSeen").asText());
            records.put(item.path("id").asText(), item);
        }
        if (records.size() > 5000) throw new UserError("Record limit reached; archive the ledger before collecting more.");
        ArrayNode items = ledger.putArray("items");
        records.values().stream().sorted(Comparator.comparing(n -> n.path("published").asText()))
                .forEach(items::add);
        ObjectNode capture = JSON.createObjectNode().put("at", clock.instant().toString()).put("feed", url)
                .put("oldest", incoming.stream().map(n -> n.path("published").asText()).min(String::compareTo).orElseThrow())
                .put("newest", incoming.stream().map(n -> n.path("published").asText()).max(String::compareTo).orElseThrow())
                .put("count", incoming.size());
        ledger.withArray("captures").add(capture);
        while (ledger.withArray("captures").size() > 800) ledger.withArray("captures").remove(0);
        atomicJson(state.resolve("records.json"), ledger);
        LOG.infof("Collected %d entries; %d stable records retained.", incoming.size(), records.size());
    }

    static void build(Map<String, String> o, Clock clock) throws Exception {
        Path state = Path.of(value(o, "state", "state"));
        ZoneId zone = ZoneId.of(value(o, "timezone", DEFAULT_ZONE.getId()));
        LocalDate start = o.containsKey("week") ? weekStart(o.get("week")) : previousWeek(clock, zone);
        Instant from = start.atStartOfDay(zone).toInstant();
        Instant until = start.plusWeeks(1).atStartOfDay(zone).toInstant();
        if (until.isAfter(clock.instant())) throw new UserError("Only completed weeks can be built.");
        String id = weekId(start);
        ObjectNode ledger = readObject(state.resolve("records.json"));
        List<ObjectNode> records = new ArrayList<>();
        for (JsonNode n : ledger.withArray("items")) {
            Instant date = Instant.parse(n.path("published").asText());
            if (!date.isBefore(from) && date.isBefore(until)) records.add(((ObjectNode) n).deepCopy());
        }
        records.sort(Comparator.comparing(n -> n.path("published").asText()));
        if (records.size() > 40) throw new UserError("More than 40 entries in one week; review the source before generation.");
        // Each snapshot covers oldest publication -> capture time. Overlapping daily snapshots
        // can bridge a busy week even after its earliest entries roll out of the latest feed.
        Instant coveredThrough = from;
        List<JsonNode> captures = new ArrayList<>();
        ledger.withArray("captures").forEach(captures::add);
        captures.sort(Comparator.comparing(c -> c.path("at").asText()));
        for (JsonNode c : captures) {
            Instant at = Instant.parse(c.path("at").asText());
            Instant oldest = Instant.parse(c.path("oldest").asText());
            if (c.path("feed").asText().equals(value(o, "feed", "https://www.the-main-thread.com/feed"))
                    && !at.isAfter(clock.instant()) && !at.isBefore(coveredThrough) && !oldest.isAfter(coveredThrough)) coveredThrough = at;
        }
        if (coveredThrough.isBefore(until)) throw new UserError("Feed captures do not cover the whole week. Recover a trusted feed snapshot first.");
        Feed.enrich(records, o);
        String model = value(o, "model", "default");
        ObjectNode input = JSON.createObjectNode().put("week", id).put("timezone", zone.getId())
                .put("generator", VERSION).put("promptVersion", Bob.PROMPT_VERSION).put("model", model);
        input.set("records", JSON.valueToTree(records));
        // Observed timestamps change daily; editorial cache follows content and verified metadata only.
        for (JsonNode n : input.withArray("records")) ((ObjectNode) n).remove(List.of("firstSeen", "lastSeen"));
        String fingerprint = sha(JSON.writeValueAsString(input));
        Path issuePath = state.resolve("issues").resolve(id + ".json");
        ObjectNode existing = Files.exists(issuePath) ? readObject(issuePath) : null;
        if (existing != null && !o.containsKey("force") && !o.containsKey("override")) {
            if (!existing.path("inputHash").asText().equals(fingerprint)) {
                throw new UserError("Accepted issue inputs changed. Review them and use --force or --override to replace this issue.");
            }
            Site.render(state, Path.of(value(o, "site", "site")), Path.of(value(o, "assets", "assets")));
            LOG.infof("Reused accepted issue %s; no Bob request.", id);
            return;
        }
        ObjectNode editorial;
        String agent = "none (empty week)";
        if (o.containsKey("override")) {
            editorial = readObject(Path.of(o.get("override")));
            agent = "editorial override";
        } else if (records.isEmpty()) {
            editorial = JSON.createObjectNode().put("headline", "A quiet week on The Main Thread")
                    .put("intro", "No articles or podcasts were published in the captured feed for this week.")
                    .put("connection", "Catch up on an earlier issue, or return to the publication for more reading.");
            editorial.putArray("items");
        } else {
            Bob.Result result = Bob.generate(input, o);
            editorial = result.editorial();
            agent = result.agent();
        }
        validate(editorial, records);
        ObjectNode issue = JSON.createObjectNode().put("schemaVersion", 1).put("id", id)
                .put("label", label(start)).put("shortLabel", label(start))
                .put("timezone", zone.getId()).put("start", from.toString()).put("endExclusive", until.toString())
                .put("generatedAt", clock.instant().toString()).put("inputHash", fingerprint)
                .put("generatorVersion", VERSION).put("promptVersion", Bob.PROMPT_VERSION)
                .put("agent", agent).put("model", model).put("acpClient", "SmallRye ACP 0.2.2")
                .put("headline", editorial.path("headline").asText()).put("intro", editorial.path("intro").asText())
                .put("connection", editorial.path("connection").asText());
        ArrayNode items = issue.putArray("items");
        for (ObjectNode record : records) {
            ObjectNode item = record.deepCopy();
            item.remove(List.of("context", "description", "headings", "firstSeen", "lastSeen", "sourceHash"));
            for (JsonNode copy : editorial.withArray("items")) if (copy.path("id").equals(item.path("id"))) {
                item.put("summary", copy.path("summary").asText());
                item.put("why", copy.path("why").asText());
                item.set("topics", copy.path("topics"));
            }
            items.add(item);
        }
        // Check the entire candidate site before replacing either the accepted issue or site.
        Path staging = Files.createTempDirectory("briefing-candidate-");
        try {
            copyIssues(state.resolve("issues"), staging.resolve("issues"));
            atomicJson(staging.resolve("issues").resolve(id + ".json"), issue);
            Site.render(staging, staging.resolve("site"), Path.of(value(o, "assets", "assets")));
            atomicJson(issuePath, issue);
            Site.install(staging.resolve("site"), Path.of(value(o, "site", "site")));
        } finally { Site.deleteTree(staging); }
        LOG.infof("Built %s: %d entries. Agent: %s.", id, records.size(), agent);
    }

    static void validate(ObjectNode copy, List<ObjectNode> records) {
        fields(copy, Set.of("headline", "intro", "connection", "items"));
        text(copy, "headline", 6, 110);
        text(copy, "intro", 20, 900);
        text(copy, "connection", 20, 1300);
        if (!copy.path("items").isArray() || copy.path("items").size() != records.size()) {
            throw new UserError("Editorial output must contain every source ID exactly once.");
        }
        Set<String> expected = new HashSet<>();
        records.forEach(n -> expected.add(n.path("id").asText()));
        for (JsonNode item : copy.path("items")) {
            fields(item, Set.of("id", "summary", "why", "topics"));
            if (!expected.remove(item.path("id").asText())) throw new UserError("Unknown or repeated editorial ID.");
            text(item, "summary", 30, 650);
            text(item, "why", 20, 300);
            JsonNode topics = item.path("topics");
            if (!topics.isArray() || topics.size() < 1 || topics.size() > 3) throw new UserError("Use 1–3 approved topics.");
            Set<String> unique = new HashSet<>();
            for (JsonNode t : topics) if (!t.isTextual() || !TOPICS.contains(t.asText()) || !unique.add(t.asText())) {
                throw new UserError("Invalid or repeated topic.");
            }
        }
    }

    static void fields(JsonNode n, Set<String> expected) {
        Set<String> found = new HashSet<>();
        n.fieldNames().forEachRemaining(found::add);
        if (!n.isObject() || !found.equals(expected)) throw new UserError("Editorial JSON has missing or unexpected fields.");
    }

    static void text(JsonNode n, String field, int min, int max) {
        JsonNode v = n.path(field);
        if (!v.isTextual() || v.asText().length() < min || v.asText().length() > max
                || v.asText().matches("(?s).*(https?://|www\\.|<|>|\\p{Cntrl}).*")) {
            throw new UserError("Editorial text exceeds limits or contains links, markup, or control characters.");
        }
    }

    static LocalDate previousWeek(Clock clock, ZoneId zone) {
        return LocalDate.now(clock.withZone(zone)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
    }

    static LocalDate weekStart(String id) {
        if (!id.matches("\\d{4}-W\\d{2}")) throw new UserError("Week must use YYYY-Www, for example 2026-W43.");
        int year = Integer.parseInt(id.substring(0, 4));
        int week = Integer.parseInt(id.substring(6));
        LocalDate result = LocalDate.of(year, 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!weekId(result).equals(id)) throw new UserError("Invalid ISO week.");
        return result;
    }

    static String weekId(LocalDate start) {
        return "%04d-W%02d".formatted(start.get(IsoFields.WEEK_BASED_YEAR), start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    static String label(LocalDate start) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM uuuu", java.util.Locale.UK);
        return fmt.format(start) + " – " + fmt.format(start.plusDays(6));
    }

    static ObjectNode readObject(Path file) throws Exception {
        if (!Files.exists(file)) return JSON.createObjectNode();
        if (Files.size(file) > 32_000_000) throw new UserError("State file exceeds the size limit.");
        JsonNode n = JSON.readTree(Files.readString(file));
        if (!n.isObject()) throw new UserError("Expected a JSON object.");
        return (ObjectNode) n;
    }

    static void atomicJson(Path file, JsonNode data) throws Exception {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".briefing-", ".tmp");
        try {
            Files.writeString(temporary, JSON.writeValueAsString(data) + "\n");
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    static void copyIssues(Path from, Path to) throws Exception {
        Files.createDirectories(to);
        if (Files.exists(from)) try (var files = Files.list(from)) {
            for (Path f : files.filter(p -> p.getFileName().toString().matches("\\d{4}-W\\d{2}\\.json")).toList()) {
                Files.copy(f, to.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static String sha(String text) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    static class UserError extends RuntimeException {
        UserError(String message) { super(message); }
    }
}
