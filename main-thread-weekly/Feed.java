import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jsoup.Jsoup;
import org.w3c.dom.Element;

final class Feed {
    static final int MAX_BYTES = 4_000_000;

    static byte[] fetch(String url) throws Exception {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new Briefing.UserError("Feed and catalog URLs must be HTTPS without credentials.");
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45))
                .header("User-Agent", "MainThreadWeekly/1 (+https://github.com/myfear/the-main-thread)").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new Briefing.UserError("Source request did not return HTTP 200. Use its final HTTPS URL.");
            byte[] bytes = body.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new Briefing.UserError("Source exceeds the 4 MB download limit.");
            return bytes;
        }
    }

    static List<ObjectNode> parse(byte[] bytes, String feed, Instant now) throws Exception {
        if (bytes.length > MAX_BYTES) throw new Briefing.UserError("Feed exceeds the 4 MB limit.");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
        });
        var document = builder.parse(new ByteArrayInputStream(bytes));
        if (!document.getDocumentElement().getTagName().equals("rss")) throw new Briefing.UserError("Expected an RSS 2.0 feed.");
        var entries = document.getElementsByTagName("item");
        if (entries.getLength() > 200) throw new Briefing.UserError("Feed exceeds 200 entries.");
        List<ObjectNode> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> urls = new HashSet<>();
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            String url = original(child(entry, "link"));
            Instant published = ZonedDateTime.parse(child(entry, "pubDate"), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            if (published.isAfter(now)) continue;
            String guid = child(entry, "guid");
            String id = Briefing.sha(guid.isBlank() ? url : guid).substring(0, 24);
            if (!ids.add(id) || !urls.add(url)) continue;
            String title = text(child(entry, "title"), 250);
            if (title.isBlank()) throw new Briefing.UserError("A source title is empty.");
            String description = plain(child(entry, "description"), 600);
            String html = child(entry, "encoded");
            var article = Jsoup.parse(html, url);
            article.select("script,style,pre,code,nav,footer,form,iframe,video,audio").remove();
            String headings = text(article.select("h2,h3").text(), 700);
            String prose = article.select("p").stream().map(org.jsoup.nodes.Element::text)
                    .filter(p -> p.length() > 60).limit(12).reduce("", (a, b) -> a + " " + b);
            String context = text(prose, 1800);
            var enclosures = entry.getElementsByTagName("enclosure");
            boolean podcast = false;
            for (int j = 0; j < enclosures.getLength(); j++) {
                if (((Element) enclosures.item(j)).getAttribute("type").startsWith("audio/")) podcast = true;
            }
            ObjectNode record = Briefing.JSON.createObjectNode().put("id", id).put("guid", text(guid, 500))
                    .put("url", url).put("title", title).put("published", published.toString())
                    .put("kind", podcast ? "podcast" : "article").put("description", description)
                    .put("headings", headings).put("context", context).put("feed", feed).put("lastSeen", now.toString());
            record.put("sourceHash", Briefing.sha(title + description + html + url + published + podcast));
            result.add(record);
        }
        return result;
    }

    static String child(Element element, String name) {
        for (var node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element e && (name.equals(e.getLocalName()) || name.equals(e.getTagName()))) return e.getTextContent().strip();
        }
        return "";
    }

    static String plain(String text, int limit) {
        return text(Jsoup.parse(text).text(), limit);
    }

    static String text(String value, int limit) {
        String clean = value.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
        return clean.length() <= limit ? clean : clean.substring(0, limit).stripTrailing() + "…";
    }

    static String original(String url) {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !Set.of("www.the-main-thread.com", "the-main-thread.com").contains(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getPath() == null || !uri.getPath().startsWith("/p/")) {
            throw new Briefing.UserError("Original links must be HTTPS article URLs on the-main-thread.com.");
        }
        return "https://www.the-main-thread.com" + uri.getPath();
    }

    static void enrich(List<ObjectNode> records, Map<String, String> options) throws Exception {
        // Catalog is optional. A catalog failure never turns an arbitrary GitHub reference into a companion.
        if (!options.containsKey("catalog") && !options.containsKey("catalog-file")) return;
        JsonNode catalog = options.containsKey("catalog-file") ? Briefing.JSON.readTree(java.nio.file.Files.readString(Path.of(options.get("catalog-file"))))
                : Briefing.JSON.readTree(fetch(options.get("catalog")));
        JsonNode entries = catalog.isArray() ? catalog : catalog.path("companions");
        if (!entries.isArray()) throw new Briefing.UserError("Catalog requires an array of companion mappings; see README.");
        for (JsonNode c : entries) {
            if (!c.path("included").asBoolean(true) || c.path("articleUrl").asText().isBlank()) continue;
            String article;
            try { article = original(c.path("articleUrl").asText()); }
            catch (IllegalArgumentException e) { continue; }
            catch (Briefing.UserError e) { continue; }
            String project = c.path("projectPath").asText(c.path("path").asText());
            if (!project.matches("[a-zA-Z0-9_-]+")) throw new Briefing.UserError("Invalid companion project path.");
            for (ObjectNode record : records) if (record.path("url").asText().equals(article)) {
                record.put("companion", "https://github.com/myfear/the-main-thread/tree/main/" + project);
                record.put("companionSource", "catalog articleUrl mapping");
            }
        }
    }
}
