import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline contract checks: dates, hostile feeds, ACP chunks, acceptance, caching, and rendered links. */
final class Checks {
    static int passed;
    interface Checked { void run() throws Exception; }

    static void run() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneId.of("UTC"));
        check(Briefing.weekStart("2026-W43").equals(LocalDate.of(2026, 10, 19)), "ISO week start");
        check(Briefing.weekId(LocalDate.of(2025, 12, 29)).equals("2026-W01"), "ISO week year boundary");
        reject(() -> Briefing.weekStart("2026-W54"), "invalid ISO week");
        check(Briefing.previousWeek(clock, Briefing.DEFAULT_ZONE).equals(LocalDate.of(2026, 10, 26)), "last completed week");
        var dst = Clock.fixed(Instant.parse("2026-10-25T22:30:00Z"), ZoneId.of("UTC"));
        check(Briefing.previousWeek(dst, Briefing.DEFAULT_ZONE).equals(LocalDate.of(2026, 10, 12)), "Sunday across DST");
        check(Briefing.previousWeek(Clock.fixed(Instant.parse("2026-10-25T23:30:00Z"), ZoneId.of("UTC")), Briefing.DEFAULT_ZONE)
                .equals(LocalDate.of(2026, 10, 19)), "local Monday across DST");
        reject(() -> Feed.original("https://evil.example/p/a"), "foreign original");
        reject(() -> Feed.original("javascript:alert(1)"), "active URL");
        reject(() -> Feed.original("https://the-main-thread.com@evil.example/p/a"), "URL credentials");
        check(Feed.original("https://the-main-thread.com/p/a?utm_source=rss").equals("https://www.the-main-thread.com/p/a"), "canonical original");
        String article = item("one", "Mon, 19 Oct 2026 08:00:00 GMT", "image/jpeg", "A &amp; B", "<p>Useful source prose about Java and predictable testing. This has enough detail for an excerpt.</p>");
        String audio = item("two", "Wed, 21 Oct 2026 08:00:00 GMT", "audio/mpeg", "A podcast", "<p>A discussion of the engineering decision and how to check it in practice.</p>");
        String before = item("older", "Sun, 18 Oct 2026 08:00:00 GMT", "image/jpeg", "Earlier", "<p>Earlier source item.</p>");
        String future = item("future", "Mon, 09 Nov 2026 08:00:00 GMT", "audio/mpeg", "Future", "");
        byte[] feed = rss(before + article + audio + article + future);
        var records = Feed.parse(feed, "https://www.the-main-thread.com/feed", clock.instant());
        check(records.size() == 3, "deduplicate GUID and exclude future dates");
        check(records.stream().filter(n -> n.path("kind").asText().equals("podcast")).count() == 1, "audio MIME classification");
        check(records.get(1).path("title").asText().equals("A & B"), "decode XML entities");
        var genericTitle = Feed.parse(rss(item("generic", "Mon, 19 Oct 2026 08:00:00 GMT", "image/jpeg", "Java Map&lt;K,V&gt;", "")), "feed", clock.instant());
        check(genericTitle.getFirst().path("title").asText().equals("Java Map<K,V>"), "preserve Java generic title text");
        check(!records.get(2).has("duration"), "no invented enclosure duration");
        reject(() -> Feed.parse("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><rss>&x;</rss>".getBytes(StandardCharsets.UTF_8), "feed", clock.instant()), "external entity rejected");
        reject(() -> Feed.parse(new byte[Feed.MAX_BYTES + 1], "feed", clock.instant()), "feed size limit");
        StringBuffer response = new StringBuffer();
        AtomicBoolean overflow = new AtomicBoolean();
        AtomicBoolean tool = new AtomicBoolean();
        Bob.receive(frame("agent_thought_chunk", "private reasoning"), response, overflow, tool);
        Bob.receive(frame("agent_message_chunk", "{\"ok\":"), response, overflow, tool);
        Bob.receive(frame("agent_message_chunk", "true}"), response, overflow, tool);
        check(response.toString().equals("{\"ok\":true}"), "ACP captures final chunks without thoughts");
        Bob.receive(frame("tool_call", "ignored"), response, overflow, tool);
        check(tool.get(), "ACP tool attempt rejected");
        Bob.receive(frame("agent_message_chunk", "a".repeat(64_001)), response, overflow, tool);
        check(overflow.get(), "ACP response bounded");
        ObjectNode copy = copy(records.subList(1, 3));
        Briefing.validate(copy, records.subList(1, 3));
        check(true, "valid editorial contract");
        ObjectNode bad = copy.deepCopy();
        bad.put("url", "https://evil.example");
        reject(() -> Briefing.validate(bad, records.subList(1, 3)), "unknown editorial field");
        ObjectNode repeated = copy.deepCopy();
        repeated.withArray("items").set(1, repeated.path("items").get(0));
        reject(() -> Briefing.validate(repeated, records.subList(1, 3)), "duplicate editorial ID");
        ObjectNode markup = copy.deepCopy();
        ((ObjectNode) markup.path("items").get(0)).put("summary", "<script>alert('unexpected')</script> this cannot be accepted");
        reject(() -> Briefing.validate(markup, records.subList(1, 3)), "editorial HTML rejected");
        ObjectNode links = copy.deepCopy();
        links.put("connection", "Open https://evil.example for a better example.");
        reject(() -> Briefing.validate(links, records.subList(1, 3)), "model-supplied links rejected");
        Path root = Files.createTempDirectory("briefing-check-");
        try {
            Path xml = root.resolve("feed.xml");
            Files.write(xml, feed);
            Path state = root.resolve("state");
            Map<String, String> collect = Map.of("state", state.toString(), "feed-file", xml.toString());
            Briefing.collect(collect, clock);
            Briefing.collect(collect, clock);
            check(Briefing.readObject(state.resolve("records.json")).path("items").size() == 3, "retained collection is idempotent");
            Path editorial = root.resolve("override.json");
            Briefing.atomicJson(editorial, copy);
            Map<String, String> build = Map.of("state", state.toString(), "site", root.resolve("site").toString(), "week", "2026-W43", "override", editorial.toString());
            Briefing.build(build, clock);
            Path accepted = state.resolve("issues/2026-W43.json");
            String acceptedJson = Files.readString(accepted);
            String html = Files.readString(root.resolve("site/index.html"));
            check(html.contains("noindex,follow"), "noindex on generated pages");
            check(html.contains("A &amp; B"), "source title escaped");
            check(!html.contains("Useful source prose"), "source excerpts absent from site");
            check(!acceptedJson.contains("\"context\""), "source context absent from accepted issue");
            check(html.contains("/p/one") && html.contains("/p/two"), "all originals in initial HTML");
            Site.verify(root.resolve("site"));
            check(true, "project-relative links and archive navigation resolve");
            Briefing.build(Map.of("state", state.toString(), "site", root.resolve("site").toString(), "week", "2026-W43"), clock);
            check(Files.readString(accepted).equals(acceptedJson), "cache rerun needs no key and preserves accepted issue");
            Briefing.atomicJson(editorial, markup);
            reject(() -> Briefing.build(build, clock), "invalid override fails before acceptance");
            check(Files.readString(accepted).equals(acceptedJson) && Files.readString(root.resolve("site/index.html")).equals(html), "failed generation preserves issue and site");
            reject(() -> Briefing.build(Map.of("state", state.toString(), "week", "2026-W43", "model", "changed-model"), clock), "input drift requires explicit replacement");
            reject(() -> Briefing.build(Map.of("state", state.toString(), "week", "2026-W41"), clock), "incomplete rolling-feed coverage fails closed");
            reject(() -> Briefing.build(Map.of("state", state.toString(), "week", "2026-W46"), clock), "unfinished week rejected");
            Briefing.build(Map.of("state", state.toString(), "site", root.resolve("site").toString(), "week", "2026-W44"), clock);
            check(Files.readString(root.resolve("site/index.html")).contains("No new posts this week."), "empty week renders without Bob");
            check(Files.exists(root.resolve("site/issues/2026-W43.html")), "previous issue survives new week");
            Site.verify(root.resolve("site"));
            ObjectNode ledger = Briefing.readObject(state.resolve("records.json"));
            var captures = ledger.putArray("captures");
            String source = "https://www.the-main-thread.com/feed";
            captures.addObject().put("feed", source).put("oldest", "2026-10-18T08:00:00Z").put("at", "2026-10-21T09:00:00Z");
            captures.addObject().put("feed", source).put("oldest", "2026-10-20T08:00:00Z").put("at", "2026-10-24T09:00:00Z");
            captures.addObject().put("feed", source).put("oldest", "2026-10-23T08:00:00Z").put("at", "2026-11-02T09:00:00Z");
            Briefing.atomicJson(state.resolve("records.json"), ledger);
            Briefing.build(Map.of("state", state.toString(), "site", root.resolve("site").toString(), "week", "2026-W43"), clock);
            check(true, "overlapping daily captures cover a week that rolled out of the latest feed");
            captures.remove(1);
            Briefing.atomicJson(state.resolve("records.json"), ledger);
            reject(() -> Briefing.build(Map.of("state", state.toString(), "week", "2026-W43"), clock), "gap between non-overlapping captures is rejected");
        } finally { Site.deleteTree(root); }
        System.out.println(passed + " offline contract checks passed.");
    }

    static ObjectNode copy(List<ObjectNode> records) {
        ObjectNode copy = Briefing.JSON.createObjectNode().put("headline", "Check the engineering decision")
                .put("intro", "A practical week about decisions that need an explicit check.")
                .put("connection", "Choose one example and make the expected behavior visible before changing its implementation.");
        var items = copy.putArray("items");
        for (ObjectNode record : records) {
            var item = items.addObject().put("id", record.path("id").asText())
                    .put("summary", "Follow the engineering decision through a concrete example and check the behavior before changing the implementation.")
                    .put("why", "Choose this when you need a practical way to test the decision.");
            item.putArray("topics").add("Java").add("Testing");
        }
        return copy;
    }
    static String frame(String type, String text) throws Exception {
        ObjectNode frame = Briefing.JSON.createObjectNode().put("method", "session/update");
        frame.putObject("params").putObject("update").put("sessionUpdate", type).putObject("content").put("type", "text").put("text", text);
        return Briefing.JSON.writeValueAsString(frame);
    }
    static byte[] rss(String items) {
        return ("<rss version=\"2.0\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\"><channel>" + items + "</channel></rss>").getBytes(StandardCharsets.UTF_8);
    }
    static String item(String slug, String date, String mime, String title, String html) {
        return "<item><title>" + title + "</title><link>https://www.the-main-thread.com/p/" + slug + "</link><guid>guid-" + slug + "</guid><pubDate>" + date + "</pubDate><description>A useful description.</description><enclosure type=\"" + mime + "\" length=\"0\" url=\"https://example.com/media\"/><content:encoded><![CDATA[" + html + "]]></content:encoded></item>";
    }
    static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        passed++;
    }
    static void reject(Checked action, String label) throws Exception {
        try { action.run(); }
        catch (Exception expected) { passed++; return; }
        throw new AssertionError(label);
    }
}
