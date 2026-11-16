import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import org.jsoup.Jsoup;

final class Site {
    static final List<String> ASSETS = List.of("app.js", "style.css", "wordmark.png", "public-sans-600.ttf", "public-sans-LICENSE.txt");

    static void render(Path state, Path destination, Path assets) throws Exception {
        List<ObjectNode> issues = new ArrayList<>();
        if (Files.exists(state.resolve("issues"))) try (var files = Files.list(state.resolve("issues"))) {
            for (Path path : files.filter(p -> p.getFileName().toString().matches("\\d{4}-W\\d{2}\\.json")).toList()) {
                ObjectNode issue = Briefing.readObject(path);
                if (!path.getFileName().toString().equals(issue.path("id").asText() + ".json")) throw new Briefing.UserError("Issue ID differs from its filename.");
                // Revalidate saved/overridden copy and original URLs before inserting any HTML.
                ObjectNode copy = issue.deepCopy();
                copy.retain("headline", "intro", "connection", "items");
                List<ObjectNode> records = new ArrayList<>();
                for (JsonNode item : copy.withArray("items")) {
                    Feed.original(item.path("url").asText());
                    if (!item.path("id").asText().matches("[a-f0-9]{24}")) throw new Briefing.UserError("Invalid stable record ID.");
                    String companion = item.path("companion").asText();
                    if (!companion.isEmpty() && !companion.matches("https://github\\.com/myfear/the-main-thread/tree/main/[A-Za-z0-9_-]+")) {
                        throw new Briefing.UserError("Invalid companion URL.");
                    }
                    records.add((ObjectNode) item.deepCopy());
                    ((ObjectNode) item).retain("id", "summary", "why", "topics");
                }
                Briefing.validate(copy, records);
                issues.add(issue);
            }
        }
        if (issues.isEmpty()) throw new Briefing.UserError("No accepted issues to render. Collect and build a completed week first.");
        issues.sort(Comparator.comparing((ObjectNode n) -> n.path("id").asText()).reversed());
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Path staged = Files.createTempDirectory(destination.toAbsolutePath().getParent(), ".briefing-site-");
        try {
            Files.createDirectories(staged.resolve("assets"));
            Files.createDirectories(staged.resolve("issues"));
            for (String asset : ASSETS) Files.copy(assets.resolve(asset), staged.resolve("assets").resolve(asset));
            for (ObjectNode issue : issues) Files.writeString(staged.resolve("issues").resolve(issue.path("id").asText() + ".html"), page(issue, issues, "../"));
            Files.writeString(staged.resolve("index.html"), page(issues.getFirst(), issues, ""));
            Files.writeString(staged.resolve("404.html"), shell("Issue not found", "", "<main id=\"main\" class=\"sources-page\"><h1>That issue is not here.</h1><p>Open the latest briefing to find the published weeks.</p><a href=\"./\">Latest briefing</a></main>"));
            Files.writeString(staged.resolve("robots.txt"), "User-agent: *\nAllow: /\n");
            Files.writeString(staged.resolve(".nojekyll"), "");
            verify(staged);
            install(staged, destination);
        } finally { deleteTree(staged); }
    }

    static String page(ObjectNode issue, List<ObjectNode> issues, String prefix) throws Exception {
        StringBuilder navigation = new StringBuilder();
        for (ObjectNode other : issues) navigation.append("<a href=\"").append(prefix).append("issues/").append(e(other.path("id").asText())).append(".html\"")
                .append(other.path("id").equals(issue.path("id")) ? " aria-current=\"page\"" : "").append(">").append(e(other.path("label").asText())).append("</a>");
        String header = "<a class=\"skip-link\" href=\"#main\">Skip to content</a><header class=\"masthead\"><a href=\"" + prefix + "index.html\" class=\"brand\" aria-label=\"The Main Thread weekly briefing\"><img src=\"" + prefix + "assets/wordmark.png\" width=\"210\" height=\"40\" alt=\"The Main Thread\"><span>Weekly briefing</span></a><nav class=\"header-nav\" aria-label=\"Issue and publication\"><details class=\"issue-menu\"><summary>" + e(issue.path("label").asText()) + icon("down") + "</summary><div class=\"issue-options\">" + navigation + "</div></details><a class=\"publication-link\" href=\"https://www.the-main-thread.com\">The publication" + icon("arrow") + "</a></nav></header>";
        TreeSet<String> tags = new TreeSet<>();
        issue.withArray("items").forEach(i -> i.path("topics").forEach(t -> tags.add(t.asText())));
        StringBuilder rail = new StringBuilder("<button data-topic-button=\"all\" aria-pressed=\"true\"><span>Everything</span><span>" + issue.path("items").size() + "</span></button>");
        for (String tag : tags) {
            long count = 0;
            for (JsonNode i : issue.path("items")) if (i.path("topics").toString().contains("\"" + tag + "\"")) count++;
            rail.append("<button data-topic-button=\"").append(e(tag)).append("\" aria-pressed=\"false\"><span>").append(e(tag)).append("</span><span>").append(count).append("</span></button>");
        }
        StringBuilder rows = new StringBuilder();
        ObjectNode inspectorHtml = Briefing.JSON.createObjectNode();
        int index = 0;
        for (JsonNode i : issue.path("items")) {
            rows.append("<article class=\"queue-row ").append(index++ == 0 ? "is-selected" : "").append("\" data-item=\"").append(e(i.path("id").asText()))
                    .append("\" data-topic=\"").append(e(String.join("|", strings(i.path("topics"))))).append("\" data-kind=\"").append(e(i.path("kind").asText()))
                    .append("\" data-search=\"").append(e((i.path("title").asText() + " " + i.path("summary").asText() + " " + String.join(" ", strings(i.path("topics")))).toLowerCase(java.util.Locale.ROOT)))
                    .append("\"><div class=\"queue-row-main\">").append(meta(i, issue)).append("<h2>").append(original(i, i.path("title").asText(), "title-link")).append("</h2><p>")
                    .append(e(i.path("why").asText())).append("</p>").append(topics(i)).append("<div class=\"queue-row-actions\"><button class=\"inspect-button enhancement\" type=\"button\" data-inspect=\"")
                    .append(e(i.path("id").asText())).append("\" aria-controls=\"reading-inspector\" aria-expanded=\"false\">Show details").append(icon("right")).append("</button>").append(mark(i)).append("</div>")
                    .append(code(i)).append("<div class=\"source-summary\">").append(e(i.path("summary").asText())).append("</div></div></article>");
            inspectorHtml.put(i.path("id").asText(), inspector(i, issue));
        }
        int total = issue.path("items").size();
        long podcasts = 0;
        for (JsonNode i : issue.path("items")) if (i.path("kind").asText().equals("podcast")) podcasts++;
        String counts = (total - podcasts) + " articles · " + podcasts + (podcasts == 1 ? " podcast" : " podcasts");
        String filters = """
                <form class="filters enhancement queue-filters" data-filter-form role="search" aria-label="Filter this issue">
                <label class="search-field"><span class="sr-only">Search this issue</span>SEARCH<input type="search" name="search" placeholder="Search this issue" autocomplete="off"></label>
                <input name="topic" type="hidden" value="all"><label class="filter-select"><span class="sr-only">Format</span><select name="format"><option value="all">All formats</option><option value="article">Articles</option><option value="podcast">Podcasts</option></select>DOWN</label>
                <label class="unread-toggle"><input name="unread" type="checkbox"><span>Unread only</span></label></form>
                <div class="result-status enhancement"><span data-result-count aria-live="polite"></span><button type="button" class="plain-button" data-clear>Clear filters</button></div>
                """.replace("SEARCH", icon("search")).replace("DOWN", icon("down"));
        String inspector = total == 0 ? "" : "<aside class=\"reading-inspector\" id=\"reading-inspector\" aria-labelledby=\"inspector-title\">" + inspector(issue.path("items").get(0), issue) + "</aside>";
        String body = header + "<main id=\"main\" class=\"queue-main\"><section class=\"queue-heading\"><div><h1>Your reading queue</h1><p>" + e(issue.path("label").asText()) + " · " + counts + "</p></div><div class=\"queue-progress enhancement\"><span data-progress></span><progress max=\"" + Math.max(1, total) + "\" value=\"0\" aria-label=\"Reading progress\" data-progress-bar></progress></div></section>"
                + "<section class=\"weekly-intro\"><h2>" + e(issue.path("headline").asText()) + "</h2><p>" + e(issue.path("intro").asText()) + "</p><details class=\"mobile-intro\"><summary>About this week" + icon("down") + "</summary><p>" + e(issue.path("intro").asText()) + "</p></details></section>"
                + "<div class=\"queue-layout\"><aside class=\"topic-rail enhancement\" aria-label=\"Topics\"><h2>Browse by topic</h2><div class=\"topic-buttons\">" + rail + "</div><p class=\"rail-note\">Read marks stay in this browser. No account needed.</p></aside><section class=\"queue-list-section\" aria-label=\"Articles and podcasts\">"
                + filters + "<div class=\"queue-list\" data-list>" + rows + "</div><div class=\"empty-state\" data-empty hidden><h3>No matches in this issue.</h3><p>Try another topic or show read items.</p><button class=\"button\" type=\"button\" data-clear>Show the whole issue</button></div>"
                + (total == 0 ? "<div class=\"empty-state\"><h2>No new posts this week.</h2><p>Choose an earlier week above, or <a href=\"https://www.the-main-thread.com\">visit the publication</a>.</p></div>" : "")
                + "</section>" + inspector + "</div><section class=\"queue-context\"><h2>How this week connects</h2><p>" + e(issue.path("connection").asText()) + "</p></section></main>"
                + "<footer class=\"site-footer\"><div><strong>A useful thread, every week.</strong><p>Read the originals. Try the code. Keep your place.</p><a href=\"https://www.the-main-thread.com/subscribe\">Subscribe to The Main Thread" + icon("arrow") + "</a></div><div class=\"footer-notes\"><p>AI-assisted briefing · originals by Markus Eisele.<br>" + e(issue.path("timezone").asText()) + " · Monday–Sunday</p><p class=\"enhancement\"><span data-progress></span> · saved on this device</p><button class=\"plain-button enhancement\" data-reset>Reset reading progress</button><details class=\"evidence\"><summary>How this briefing was made</summary><div class=\"evidence-body\"><p>RSS records were normalized in Java, processed through " + e(issue.path("acpClient").asText()) + " and " + e(issue.path("agent").asText()) + ", then validated before rendering.</p><p>Generated " + e(issue.path("generatedAt").asText()) + ". Prompt " + e(issue.path("promptVersion").asText()) + ". Model: " + e(issue.path("model").asText()) + ".</p><p>The feed is a rolling source. Coverage means a post-week capture still reached the start of this week. Summaries can contain mistakes; use the originals for full context.</p><p>Only reading marks are stored on this device. No analytics or account.</p></div></details></div></footer><p class=\"sr-only\" role=\"status\" aria-live=\"polite\" data-notice></p>";
        ObjectNode payload = Briefing.JSON.createObjectNode().put("concept", "queue");
        payload.set("issue", issue);
        payload.set("inspectorHtml", inspectorHtml);
        String json = Briefing.JSON.writeValueAsString(payload).replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
        return shell(issue.path("label").asText() + " · Reading queue", prefix,
                "<script type=\"application/json\" id=\"issue-data\">" + json + "</script><script src=\"" + prefix + "assets/app.js\" defer></script><div class=\"page-shell\">" + body + "</div><noscript><div class=\"noscript-note\">Original links and summaries work without JavaScript. Filters and saved reading progress need JavaScript.</div></noscript>");
    }

    static String shell(String title, String prefix, String body) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><meta name=\"robots\" content=\"noindex,follow\"><meta name=\"color-scheme\" content=\"light\"><title>" + e(title) + " · The Main Thread</title><link rel=\"stylesheet\" href=\"" + prefix + "assets/style.css\"></head><body class=\"concept-queue\">" + body + "</body></html>";
    }

    static String inspector(JsonNode item, JsonNode issue) {
        return "<div class=\"inspector-inner\">" + meta(item, issue) + "<h2 id=\"inspector-title\">" + e(item.path("title").asText()) + "</h2><p>" + e(item.path("summary").asText()) + "</p><p class=\"why-note\">" + e(item.path("why").asText()) + "</p>" + topics(item) + original(item, item.path("kind").asText().equals("podcast") ? "Listen on The Main Thread" : "Read on The Main Thread", "button") + code(item) + mark(item)
                + "<details class=\"evidence\"><summary>About this source" + icon("down") + "</summary><div class=\"evidence-body\"><p>Published " + e(item.path("published").asText()) + ". Included from <a href=\"https://www.the-main-thread.com/feed\">the publication feed</a>.</p><p>" + (item.has("companion") ? "The companion was matched through an explicit article URL mapping. No build status is asserted by this briefing." : "No verified companion mapping was loaded for this post.") + "</p></div></details></div>";
    }

    static String original(JsonNode i, String label, String cls) {
        return "<a class=\"" + cls + "\" href=\"" + e(i.path("url").asText()) + "\">" + e(label) + icon("arrow") + "</a>";
    }
    static String code(JsonNode i) {
        return i.hasNonNull("companion") ? "<a class=\"code-link\" href=\"" + e(i.path("companion").asText()) + "\">Companion code" + icon("arrow") + "</a>" : "";
    }
    static String mark(JsonNode i) {
        return "<button class=\"read-button enhancement\" data-read=\"" + e(i.path("id").asText()) + "\" aria-pressed=\"false\" aria-label=\"Mark " + e(i.path("title").asText()) + " as read\"><span class=\"check-circle\">" + icon("check") + "</span><span data-read-label>Mark read</span></button>";
    }
    static String topics(JsonNode i) {
        StringBuilder html = new StringBuilder("<div class=\"topics\">");
        i.path("topics").forEach(t -> html.append("<span>").append(e(t.asText())).append("</span>"));
        return html + "</div>";
    }
    static String meta(JsonNode i, JsonNode issue) {
        String date = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.UK).withZone(ZoneId.of(issue.path("timezone").asText())).format(Instant.parse(i.path("published").asText()));
        return "<div class=\"item-meta\"><span>" + (i.path("kind").asText().equals("podcast") ? "Podcast" : "Article") + "</span><span>" + e(date) + "</span></div>";
    }
    static List<String> strings(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(n -> result.add(n.asText()));
        return result;
    }
    static String e(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    static String icon(String name) {
        String shape = switch (name) {
            case "down" -> "<path d=\"m6 9 6 6 6-6\"/>";
            case "right" -> "<path d=\"M4 12h16m-6-6 6 6-6 6\"/>";
            case "check" -> "<path d=\"m5 12 4 4L19 6\"/>";
            case "search" -> "<circle cx=\"10.5\" cy=\"10.5\" r=\"6.5\"/><path d=\"m16 16 5 5\"/>";
            default -> "<path d=\"M7 17 17 7M7 7h10v10\"/>";
        };
        return "<svg class=\"icon\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\">" + shape + "</svg>";
    }

    static void verify(Path site) throws Exception {
        try (var files = Files.walk(site)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".html")).toList()) {
                var doc = Jsoup.parse(Files.readString(path));
                if (!doc.select("meta[name=robots]").attr("content").equals("noindex,follow")) throw new Briefing.UserError("Every page must declare noindex,follow.");
                for (var link : doc.select("a[href],link[href],script[src],img[src]")) {
                    String url = link.hasAttr("href") ? link.attr("href") : link.attr("src");
                    if (url.startsWith("https://") || url.startsWith("#")) continue;
                    Path target = path.getParent().resolve(url).normalize();
                    if (!target.startsWith(site) || !Files.exists(target)) throw new Briefing.UserError("A generated local link is broken.");
                }
            }
        }
    }

    static void install(Path staged, Path destination) throws Exception {
        Path target = destination.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path old = target.resolveSibling(target.getFileName() + ".previous");
        if (Files.exists(old)) throw new Briefing.UserError("A previous site backup exists; recover it before rendering again.");
        boolean moved = false;
        try {
            if (Files.exists(target)) { Files.move(target, old, StandardCopyOption.ATOMIC_MOVE); moved = true; }
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            if (moved && !Files.exists(target)) Files.move(old, target, StandardCopyOption.ATOMIC_MOVE);
            throw e;
        }
        if (moved) deleteTree(old);
    }

    static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
