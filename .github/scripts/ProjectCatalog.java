//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.21.5

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Repository catalog and CI reporting. Run with JBang; no project build is needed. */
public class ProjectCatalog {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String START = "<!-- BUILD_BADGES_START -->";
    static final String END = "<!-- BUILD_BADGES_END -->";
    static final Set<String> BUILD_EXCLUDES = Set.of("video-pipeline");
    static final Set<String> OVERRIDE_FIELDS = Set.of("include", "articleUrl", "java", "quarkus");
    static final Pattern ACTION = Pattern.compile("(?m)^\\s*(?:-\\s*)?uses:\\s*([^\\s#]+)");

    record Project(String path, boolean included, boolean buildable, String java, String quarkus,
                   String articleUrl, String buildJava, boolean protoc) {}
    record Build(String path, String java, boolean protoc) {}
    record Tests(long total, long passed, long failed, long errors, long skipped) {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: jbang ProjectCatalog.java discover|matrix|validate|aggregate|render [--root DIR] [options]");
            System.exit(2);
        }
        Map<String, String> options = options(args);
        Path root = Path.of(options.getOrDefault("root", ".")).toAbsolutePath().normalize();
        switch (args[0]) {
            case "discover" -> output(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(discover(root)), options);
            case "validate" -> {
                var projects = discover(root);
                validatePins(root);
                System.out.println("Validated " + projects.stream().filter(Project::included).count() + " catalog entries.");
            }
            case "matrix" -> {
                var matrix = matrix(root, required(options, "event"), options.getOrDefault("base", ""),
                        options.getOrDefault("head", "HEAD"), Boolean.parseBoolean(options.getOrDefault("build-all", "false")));
                output(JSON.writeValueAsString(matrix), options);
                if (options.containsKey("github-output")) {
                    Files.writeString(Path.of(options.get("github-output")), "matrix=" + JSON.writeValueAsString(matrix)
                            + "\nhas-changes=" + !matrix.isEmpty() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            }
            case "aggregate" -> aggregate(root, Path.of(required(options, "matrix")),
                    Path.of(required(options, "results")), Path.of(required(options, "badges")),
                    required(options, "source-sha"), Long.parseLong(required(options, "run-number")),
                    Long.parseLong(required(options, "run-attempt")));
            case "render" -> {
                Path readme = root.resolve("README.md");
                String original = Files.readString(readme);
                String rendered = replaceTable(original, table(discover(root),
                        options.containsKey("badges") ? Path.of(options.get("badges")) : null,
                        options.getOrDefault("repository", "myfear/the-main-thread")));
                if (options.containsKey("check")) {
                    if (!rendered.equals(original)) throw new IllegalStateException("README catalog needs regeneration.");
                } else if (options.containsKey("write")) {
                    if (!rendered.equals(original)) Files.writeString(readme, rendered);
                } else if (options.containsKey("output")) {
                    Path output = Path.of(options.get("output")).toAbsolutePath();
                    Files.createDirectories(output.getParent());
                    Files.writeString(output, rendered);
                } else System.out.print(rendered);
            }
            default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
        }
    }

    static Map<String, String> options(String[] args) {
        var result = new HashMap<String, String>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("Expected --option: " + args[i]);
            String key = args[i].substring(2);
            boolean flag = Set.of("write", "check").contains(key);
            if (!flag && i + 1 == args.length) throw new IllegalArgumentException("Missing value for --" + key);
            String value = flag ? "true" : args[++i];
            if (result.put(key, value) != null) throw new IllegalArgumentException("Duplicate option: " + key);
        }
        return result;
    }

    static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing --" + key);
        return value;
    }

    static void output(String value, Map<String, String> options) throws IOException {
        if (options.containsKey("output")) write(Path.of(options.get("output")), value);
        else System.out.print(value.endsWith("\n") ? value : value + "\n");
    }

    static void write(Path path, String value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, value.endsWith("\n") ? value : value + "\n");
    }

    static String git(Path root, String... args) throws Exception {
        var command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IOException("Git command failed: " + command);
        return output;
    }

    static List<String> tracked(Path root) throws Exception {
        // --cached includes newly staged files, but never ignored or untracked local companions.
        return Arrays.stream(git(root, "ls-files", "--cached", "-z").split("\0"))
                .filter(s -> !s.isEmpty()).distinct().sorted().toList();
    }

    static List<Project> discover(Path root) throws Exception {
        List<String> files = tracked(root);
        var groups = new TreeMap<String, List<String>>();
        for (String file : files) {
            int slash = file.indexOf('/');
            if (slash > 0 && !file.startsWith(".") && Files.exists(root.resolve(file), LinkOption.NOFOLLOW_LINKS))
                groups.computeIfAbsent(file.substring(0, slash), k -> new ArrayList<>()).add(file);
        }
        var projects = new ArrayList<Project>();
        for (var entry : groups.entrySet()) {
            String name = entry.getKey();
            JsonNode overrides = JSON.createObjectNode();
            String overridePath = name + "/.companion.json";
            if (entry.getValue().contains(overridePath)) {
                overrides = JSON.readTree(safeFile(root, overridePath).toFile());
                if (overrides == null || !overrides.isObject()) throw new IllegalArgumentException(overridePath + " must contain an object");
                for (var field : overrides.properties()) {
                    if (!OVERRIDE_FIELDS.contains(field.getKey())) throw new IllegalArgumentException(overridePath + ": unknown field " + field.getKey());
                    if (field.getKey().equals("include") ? !field.getValue().isBoolean() : !field.getValue().isTextual())
                        throw new IllegalArgumentException(overridePath + ": invalid type for " + field.getKey());
                }
            }
            var poms = new TreeMap<String, Map<String, String>>();
            for (String file : entry.getValue()) {
                if (file.endsWith("/pom.xml") && !file.contains("/target/") && Files.exists(root.resolve(file)))
                    poms.put(file, properties(safeFile(root, file)));
            }
            Map<String, String> top = poms.getOrDefault(name + "/pom.xml", Map.of());
            var children = poms.entrySet().stream().filter(e -> !e.getKey().equals(name + "/pom.xml"))
                    .map(Map.Entry::getValue).toList();
            String java = override(overrides, "java", first(javaVersion(top), consistent(children.stream().map(ProjectCatalog::javaVersion).toList())));
            String quarkus = override(overrides, "quarkus", first(value(top, "quarkus.platform.version"),
                    consistent(children.stream().map(p -> value(p, "quarkus.platform.version")).toList())));
            String article = override(overrides, "articleUrl", first(value(top, "substack-article.url"),
                    consistent(children.stream().map(p -> value(p, "substack-article.url")).toList())));
            if (!article.isEmpty() && !validArticle(article)) {
                if (overrides.has("articleUrl")) throw new IllegalArgumentException(overridePath + ": invalid articleUrl: " + article);
                System.err.println("WARNING: " + name + ": invalid article URL omitted: " + article);
                article = "";
            }
            // Catalog overrides must not change the existing build eligibility or Java selection.
            // A reactor can require a newer build JDK than its application's bytecode target.
            String buildJava = value(top, "maven.build.java.version");
            if (top.containsKey("maven.build.java.version") && !buildJava.matches("[0-9]+"))
                throw new IllegalArgumentException(name + "/pom.xml: maven.build.java.version must resolve to a Java major version");
            if (buildJava.isEmpty()) buildJava = javaVersion(top);
            if (buildJava.isEmpty()) buildJava = poms.entrySet().stream()
                    .filter(e -> e.getKey().split("/").length == 3).map(e -> javaVersion(e.getValue()))
                    .filter(s -> !s.isEmpty()).findFirst().orElse("21");
            boolean buildable = poms.containsKey(name + "/pom.xml") && !BUILD_EXCLUDES.contains(name);
            boolean protoc = buildable && Files.readString(safeFile(root, name + "/pom.xml")).contains("<artifactId>protobuf-maven-plugin</artifactId>");
            projects.add(new Project(name, overrides.path("include").asBoolean(true), buildable,
                    java, quarkus, article, buildJava, protoc));
        }
        return projects;
    }

    static Path safeFile(Path root, String file) throws IOException {
        Path path = root.resolve(file).toRealPath();
        if (!path.startsWith(root.toRealPath())) throw new IOException("File escapes repository: " + file);
        return path;
    }

    static Document xml(Path file) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(file.toFile());
    }

    static Map<String, String> properties(Path pom) throws Exception {
        var values = new HashMap<String, String>();
        NodeList children = xml(pom).getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element e && e.getLocalName().equals("properties")) {
                NodeList props = e.getChildNodes();
                for (int j = 0; j < props.getLength(); j++) if (props.item(j) instanceof Element p)
                    values.put(p.getLocalName(), p.getTextContent().strip());
            }
        }
        return values;
    }

    static String value(Map<String, String> properties, String key) {
        String value = properties.getOrDefault(key, "");
        var seen = new HashSet<String>();
        while (value.matches("\\$\\{[^}]+}") && seen.add(value)) value = properties.getOrDefault(value.substring(2, value.length() - 1), "");
        return value.contains("${") ? "" : value;
    }

    static String javaVersion(Map<String, String> properties) {
        for (String key : List.of("maven.compiler.release", "java.version", "maven.compiler.source")) {
            String version = value(properties, key);
            if (version.matches("[0-9]+")) return version;
            if (version.matches("1\\.[0-9]+")) return version.substring(2);
        }
        return "";
    }

    static String first(String a, String b) { return a.isEmpty() ? b : a; }
    static String override(JsonNode node, String key, String fallback) { return node.has(key) ? node.get(key).asText().strip() : fallback; }
    static String consistent(List<String> values) {
        var unique = new HashSet<>(values);
        unique.remove("");
        return unique.size() == 1 ? unique.iterator().next() : "";
    }

    static boolean validArticle(String value) {
        try {
            URI uri = URI.create(value);
            return Set.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null
                    && uri.getHost().contains(".") && uri.getUserInfo() == null
                    && !uri.getHost().equals("www.the-main-thread-com")
                    && !uri.getHost().equals("the-main-thread-com");
        } catch (IllegalArgumentException e) { return false; }
    }

    static List<Build> matrix(Path root, String event, String base, String head, boolean buildAll) throws Exception {
        var projects = discover(root);
        if (event.equals("workflow_dispatch") && !buildAll) return List.of();
        boolean all = event.equals("workflow_dispatch") || (event.equals("push") && base.matches("0+"));
        Set<String> changed = new HashSet<>();
        if (!all) {
            if (base.isBlank()) throw new IllegalArgumentException("A base commit is required for " + event);
            changed.addAll(Arrays.asList(git(root, "diff", "--name-only", "-z", base, head, "--").split("\0")));
            all = changed.stream().anyMatch(p -> p.equals(".github/workflows/monorepo-build.yml")
                    || p.startsWith(".github/scripts/") || p.startsWith(".github/actions/"));
        }
        final boolean selectAll = all;
        return projects.stream().filter(Project::buildable)
                .filter(p -> selectAll || changed.stream().anyMatch(f -> f.startsWith(p.path() + "/")))
                .map(p -> new Build(p.path(), p.buildJava(), p.protoc())).toList();
    }

    static Tests tests(Path reports) throws Exception {
        if (!Files.exists(reports)) return null;
        long total = 0, failed = 0, errors = 0, skipped = 0;
        boolean found = false;
        try (Stream<Path> paths = Files.walk(reports)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().startsWith("TEST-") && p.toString().endsWith(".xml")).sorted().toList()) {
                found = true;
                Document document = xml(path);
                NodeList suites = document.getElementsByTagName("testsuite");
                if (suites.getLength() == 0) throw new IOException("No testsuite in " + path);
                for (int i = 0; i < suites.getLength(); i++) {
                    Element suite = (Element) suites.item(i);
                    // A container testsuite can summarize its children: count only leaves.
                    if (suite.getElementsByTagName("testsuite").getLength() > 0) continue;
                    long t = count(suite, "tests"), f = count(suite, "failures"), e = count(suite, "errors"), s = count(suite, "skipped");
                    if (t < f + e + s) throw new IOException("Inconsistent test counts in " + path);
                    total += t; failed += f; errors += e; skipped += s;
                }
            }
        }
        return found ? new Tests(total, total - failed - errors - skipped, failed, errors, skipped) : null;
    }

    static long count(Element suite, String key) {
        String value = suite.getAttribute(key);
        long count = value.isEmpty() ? 0 : Long.parseLong(value);
        if (count < 0) throw new IllegalArgumentException("Negative test count: " + key);
        return count;
    }

    static boolean older(JsonNode previous, long number, long attempt) {
        long oldNumber = previous.path("runNumber").asLong(0), oldAttempt = previous.path("runAttempt").asLong(0);
        return number < oldNumber || (number == oldNumber && attempt < oldAttempt);
    }

    static void aggregate(Path root, Path matrixPath, Path results, Path badges, String sha, long number, long attempt) throws Exception {
        if (!sha.matches("[a-fA-F0-9]{40}") || number < 1 || attempt < 1) throw new IllegalArgumentException("Invalid run provenance");
        JsonNode matrix = JSON.readTree(matrixPath.toFile());
        if (!matrix.isArray()) throw new IllegalArgumentException("Matrix must be an array");
        var expected = new TreeSet<String>();
        for (JsonNode entry : matrix) {
            String name = entry.path("path").asText();
            if (name.isBlank() || name.startsWith(".") || name.contains("/") || name.contains("\\") || !expected.add(name))
                throw new IllegalArgumentException("Invalid or duplicated matrix project: " + name);
        }
        var current = new HashSet<String>();
        for (Project p : discover(root)) if (p.buildable()) current.add(p.path());
        for (String name : expected) {
            if (!current.contains(name)) continue; // Project deleted or no longer built on current main.
            Path metadataPath = badges.resolve(name + "-metadata.json");
            JsonNode previous = readJson(metadataPath);
            if (older(previous, number, attempt)) continue;
            Path artifact = results.resolve("result-" + name);
            String status = "no result", reason = "Expected build artifact is missing";
            Tests tests = null;
            long resultAttempt = attempt;
            if (Files.isRegularFile(artifact.resolve("status.properties"))) {
                try {
                    var props = new Properties();
                    try (Reader reader = Files.newBufferedReader(artifact.resolve("status.properties"))) { props.load(reader); }
                    long artifactAttempt = Long.parseLong(props.getProperty("runAttempt", "0"));
                    if (!name.equals(props.getProperty("project")) || !sha.equals(props.getProperty("sourceSha"))
                            || !Long.toString(number).equals(props.getProperty("runNumber"))
                            || artifactAttempt < 1 || artifactAttempt > attempt)
                        throw new IOException("Artifact provenance does not match this run");
                    // Re-running only failed jobs reuses successful jobs' earlier artifacts.
                    resultAttempt = artifactAttempt;
                    status = props.getProperty("status", "");
                    if (!Set.of("passing", "failing").contains(status)) throw new IOException("Invalid build status");
                    reason = props.getProperty("reason", "");
                    tests = tests(artifact.resolve("reports"));
                    if (tests != null && tests.failed() + tests.errors() > 0) status = "failing";
                } catch (Exception e) {
                    status = "no result"; reason = "Invalid result: " + e.getMessage(); tests = null;
                    System.err.println("WARNING: " + name + ": " + reason);
                }
            }
            if (older(previous, number, resultAttempt)) continue;
            var metadata = JSON.createObjectNode();
            metadata.put("projectPath", name).put("sourceSha", sha).put("runNumber", number).put("runAttempt", resultAttempt)
                    .put("status", status).put("reason", reason);
            metadata.set("tests", JSON.valueToTree(tests));
            var badge = JSON.createObjectNode().put("schemaVersion", 1).put("label", "build").put("message", status)
                    .put("color", status.equals("passing") ? "green" : status.equals("failing") ? "red" : "lightgrey");
            write(metadataPath, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(metadata));
            write(badges.resolve(name + ".json"), JSON.writeValueAsString(badge));
        }
    }

    static JsonNode readJson(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return JSON.createObjectNode();
        JsonNode value = JSON.readTree(path.toFile());
        if (value == null || !value.isObject()) throw new IOException("Expected JSON object: " + path);
        return value;
    }

    static String table(List<Project> projects, Path badges, String repository) throws IOException {
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Invalid repository");
        var table = new StringBuilder("| Project | Java | Quarkus | Tests | Links | Build Status |\n| :--- | :---: | :---: | :---: | :---: | :--- |\n");
        for (Project p : projects) {
            if (!p.included()) continue;
            String encoded = encode(p.path());
            String status = "Not configured", test = "—";
            if (p.buildable()) {
                status = "Not yet built";
                if (badges != null && Files.isRegularFile(badges.resolve(p.path() + ".json"))) {
                    JsonNode badge = readJson(badges.resolve(p.path() + ".json"));
                    if (!Set.of("passing", "failing", "no result").contains(badge.path("message").asText()))
                        throw new IOException("Invalid badge status for " + p.path());
                    status = "![" + escape(p.path()) + "](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/"
                            + repository + "/badges/" + encoded + ".json)";
                    JsonNode metadata = readJson(badges.resolve(p.path() + "-metadata.json"));
                    test = testDisplay(metadata.path("tests"));
                }
            }
            String article = p.articleUrl().isEmpty() ? "—" : "[Article](" + p.articleUrl().replace("(", "%28").replace(")", "%29").replace("|", "%7C") + ")";
            table.append("| **[").append(escape(p.path())).append("](https://github.com/").append(repository)
                    .append("/tree/main/").append(encoded).append(")** | ").append(display(p.java())).append(" | ")
                    .append(display(p.quarkus())).append(" | ").append(test).append(" | ").append(article).append(" | ").append(status).append(" |\n");
        }
        return table.toString();
    }

    static String testDisplay(JsonNode tests) {
        if (!tests.isObject() || tests.path("total").asLong() == 0) return "—";
        long total = tests.path("total").asLong(), passed = tests.path("passed").asLong(), skipped = tests.path("skipped").asLong();
        long problems = tests.path("failed").asLong() + tests.path("errors").asLong();
        return (problems > 0 ? "❌ " : passed == 0 ? "⏭️ " : "✅ ") + passed + "/" + total + (skipped > 0 ? " (⏭️ " + skipped + ")" : "");
    }

    static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    static String display(String value) { return value.isEmpty() ? "—" : escape(value); }
    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("|", "&#124;")
                .replace("[", "&#91;").replace("]", "&#93;").replace("\r", " ").replace("\n", " ");
    }

    static String replaceTable(String readme, String table) {
        int start = readme.indexOf(START), end = readme.indexOf(END);
        if (start < 0 || end < start || readme.indexOf(START, start + 1) >= 0 || readme.indexOf(END, end + 1) >= 0)
            throw new IllegalArgumentException("README must contain exactly one ordered pair of catalog markers");
        String newline = readme.contains("\r\n") ? "\r\n" : "\n";
        return readme.substring(0, start + START.length()) + newline + table.replace("\n", newline) + readme.substring(end);
    }

    static Map<String, String> actionRefs(Path file) throws IOException {
        var refs = new LinkedHashMap<String, String>();
        var matcher = ACTION.matcher(Files.readString(file));
        while (matcher.find()) {
            String ref = matcher.group(1);
            if (ref.startsWith("./")) continue;
            int at = ref.lastIndexOf('@');
            if (at < 1 || !ref.substring(at + 1).matches("[a-f0-9]{40}")) throw new IllegalArgumentException(file + ": action must use a full commit SHA: " + ref);
            String name = ref.substring(0, at), pin = ref.substring(at + 1);
            String old = refs.put(name, pin);
            if (old != null && !old.equals(pin)) throw new IllegalArgumentException(file + ": conflicting pins for " + name);
        }
        return refs;
    }

    static void validatePins(Path root) throws Exception {
        var canonical = new HashMap<String, String>();
        try (var paths = Files.walk(root.resolve(".github"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml")).toList()) {
                for (var ref : actionRefs(path).entrySet()) {
                    String old = canonical.put(ref.getKey(), ref.getValue());
                    if (old != null && !old.equals(ref.getValue())) throw new IllegalArgumentException("Conflicting active action pins for " + ref.getKey());
                }
            }
        }
        for (String file : tracked(root)) {
            if (!file.startsWith(".github/") && file.contains("/.github/workflows/") && (file.endsWith(".yml") || file.endsWith(".yaml"))) {
                for (var ref : actionRefs(root.resolve(file)).entrySet()) {
                    if (canonical.containsKey(ref.getKey()) && !canonical.get(ref.getKey()).equals(ref.getValue()))
                        throw new IllegalArgumentException(file + ": update " + ref.getKey() + " to " + canonical.get(ref.getKey()) + " to match the active workflow");
                }
            }
        }
    }
}
