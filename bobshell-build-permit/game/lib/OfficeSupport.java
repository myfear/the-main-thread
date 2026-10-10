import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Shared protocol and office operations. Stdout is a response channel, not a log. */
class OfficeSupport {
    static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static final String CASE = "BUILD-001";
    static final String WHEEL = "src/main/java/office/StampWheel.java";
    static final List<String> COMMANDS = List.of("jbang tools/Office.java status",
            "jbang tools/Office.java verify", "jbang tools/Office.java issue");

    static ObjectNode object() {
        return JSON.createObjectNode();
    }

    static JsonNode read(Path path) throws IOException {
        JsonNode value = JSON.readTree(Files.readString(path));
        if (value == null) {
            throw new IOException("Empty JSON: " + path.getFileName());
        }
        return value;
    }

    static void write(Path path, JsonNode value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), ".office-write-", ".tmp");
        try {
            Files.writeString(temporary, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n");
            Files.move(temporary, path.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void output(JsonNode value) throws IOException {
        System.out.println(JSON.writeValueAsString(value));
    }

    static ObjectNode record() {
        return object().put("at", Instant.now().toString());
    }

    static String runId(Path root) throws IOException {
        String id = read(root.resolve(".office/run.json")).path("runId").asText();
        if (id.isBlank()) {
            throw new IOException("Run is not initialized. Use Hunt.java init.");
        }
        return id;
    }

    static String fingerprint(Path root) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        var names = new ArrayList<String>(List.of("pom.xml", "mvnw", "mvnw.cmd",
                "application.json", "START_HERE.md"));
        for (String directory : List.of("src", "office", ".mvn", "tools", "lib", ".bob")) {
            try (var files = Files.walk(root.resolve(directory))) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    names.add(root.relativize(file).toString());
                }
            }
        }
        names.sort(String::compareTo);
        for (String name : names) {
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(root.resolve(name)));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String expectedKey(Path root) throws IOException {
        var fragments = new ArrayList<JsonNode>();
        for (JsonNode row : read(root.resolve("office/register.json"))) {
            if (row.path("current").asBoolean()) {
                fragments.add(row);
            }
        }
        fragments.sort(Comparator.comparingInt(row -> row.path("counter").asInt()));
        StringBuilder key = new StringBuilder();
        for (JsonNode row : fragments) {
            key.append(row.path("fragment").asText());
        }
        if (key.isEmpty()) {
            throw new IOException("The register has no current key.");
        }
        return key.toString();
    }

    static boolean keyCorrect(Path root) throws IOException {
        JsonNode form = read(root.resolve("application.json"));
        return CASE.equals(form.path("caseId").asText())
                && expectedKey(root).equals(form.path("key").asText());
    }

    static String refusal(Path root) throws Exception {
        if (!keyCorrect(root)) {
            return "The cabinet key is missing or incorrect. Consult reception.";
        }
        Path path = root.resolve("target/verification.json");
        if (!Files.exists(path)) {
            return "Your application has no test stamp. Run jbang tools/Office.java verify.";
        }
        JsonNode stamp = read(path);
        if (!runId(root).equals(stamp.path("runId").asText())) {
            return "This stamp belongs to another case run. Verify this application again.";
        }
        if (!fingerprint(root).equals(stamp.path("fingerprint").asText())) {
            return "Your stamp refers to an earlier version of this application. Verify again.";
        }
        if (!CASE.equals(stamp.path("caseId").asText()) || !stamp.path("ok").asBoolean()
                || !stamp.path("keyStamp").asBoolean() || !stamp.path("testStamp").asBoolean()
                || !stamp.path("inputsUnchanged").asBoolean()) {
            return "The test department refused your stamp. Repair the Java code and verify again.";
        }
        return null;
    }

    static ObjectNode status(Path root) throws Exception {
        String reason = refusal(root);
        var result = object().put("caseId", CASE).put("runId", runId(root))
                .put("keyStamp", keyCorrect(root)).put("ready", reason == null);
        if (reason != null) {
            result.put("reason", reason);
        }
        Path permit = root.resolve("permit.json");
        JsonNode issued = Files.exists(permit) ? read(permit) : object();
        result.put("permitValid", reason == null && issued.path("ok").asBoolean()
                && CASE.equals(issued.path("caseId").asText())
                && runId(root).equals(issued.path("runId").asText())
                && fingerprint(root).equals(issued.path("fingerprint").asText()));
        return result;
    }

    record Result(int exitCode, String output) {
    }

    static void terminate(Process process) {
        var children = process.descendants().toList();
        children.forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }

    static Result run(Path root, List<String> command, Map<String, String> environment, int seconds)
            throws Exception {
        return runWithInput(root, command, environment, seconds, null);
    }

    static Result runWithInput(Path root, List<String> command, Map<String, String> environment,
            int seconds, String input) throws Exception {
        var builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        if (input != null) {
            process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        }
        process.getOutputStream().close();
        Thread shutdown = new Thread(() -> terminate(process));
        Runtime.getRuntime().addShutdownHook(shutdown);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var reader = executor.submit(() -> {
                var saved = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = process.getInputStream().read(buffer)) != -1) {
                    int capacity = 8 * 1024 * 1024 - saved.size();
                    if (capacity > 0) {
                        saved.write(buffer, 0, Math.min(count, capacity));
                    }
                }
                return saved.toString(StandardCharsets.UTF_8);
            });
            boolean done = process.waitFor(seconds, TimeUnit.SECONDS);
            if (!done) {
                terminate(process);
            }
            return new Result(done ? process.exitValue() : 124, reader.get(5, TimeUnit.SECONDS));
        } finally {
            Runtime.getRuntime().removeShutdownHook(shutdown);
        }
    }

    static Result run(Path root, int seconds, String... command) throws Exception {
        return run(root, List.of(command), Map.of(), seconds);
    }

    static String tail(String text) {
        return text.length() <= 2400 ? text : text.substring(text.length() - 2400);
    }

    static ObjectNode verify(Path root) throws Exception {
        String before = fingerprint(root);
        var build = run(root, 90, "./mvnw", "test", "-q");
        String after = fingerprint(root);
        boolean unchanged = before.equals(after);
        boolean key = keyCorrect(root);
        var result = record().put("caseId", CASE).put("runId", runId(root))
                .put("fingerprint", before).put("inputsUnchanged", unchanged)
                .put("keyStamp", key).put("testStamp", build.exitCode() == 0)
                .put("exitCode", build.exitCode()).put("ok", key && build.exitCode() == 0 && unchanged)
                .put("output", tail(build.output()));
        write(root.resolve("target/verification.json"), result);
        return result;
    }

    static ObjectNode issue(Path root) throws Exception {
        String reason = refusal(root);
        if (reason != null) {
            return object().put("ok", false).put("reason", reason);
        }
        var permit = record().put("caseId", CASE).put("runId", runId(root))
                .put("fingerprint", fingerprint(root)).put("ok", true)
                .put("message", "BUILD PERMIT ISSUED. Please retain this JSON for your records.");
        write(root.resolve("permit.json"), permit);
        return permit;
    }

    static void audit(Path root, JsonNode event, String decision, String reason) throws IOException {
        var row = record().put("runId", runId(root)).put("session", event.path("session_id").asText())
                .put("event", event.path("hook_event_name").asText())
                .put("tool", event.path("tool_name").asText())
                .put("toolUseId", event.path("tool_use_id").asText()).put("decision", decision);
        JsonNode input = event.path("tool_input");
        for (String field : List.of("command", "path", "file_path", "cwd")) {
            if (input.has(field)) {
                row.put(field, input.path(field).asText());
            }
        }
        if (reason != null) {
            row.put("reason", reason);
        }
        Files.createDirectories(root.resolve(".office"));
        Files.writeString(root.resolve(".office/events.jsonl"), JSON.writeValueAsString(row) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static List<JsonNode> events(Path root) throws IOException {
        var result = new ArrayList<JsonNode>();
        Path file = root.resolve(".office/events.jsonl");
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file)) {
                if (!line.isBlank()) {
                    result.add(JSON.readTree(line));
                }
            }
        }
        return result;
    }
}
