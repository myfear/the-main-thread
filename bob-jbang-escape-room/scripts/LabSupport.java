import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

class LabSupport implements AutoCloseable {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String OPEN = "printf 'AIRLOCK_OPEN\\n'";
    final Path root;
    final Path workspace;
    final String jbang;
    final Map<String, String> environment = new HashMap<>(System.getenv());

    LabSupport() throws Exception {
        root = Path.of("").toAbsolutePath().normalize();
        require(Files.isRegularFile(root.resolve("demo/verify.java")),
                "Run this helper from the bob-jbang-escape-room directory.");
        jbang = environment.containsKey("JBANG_CMD") ? environment.get("JBANG_CMD") : executable("jbang");
        environment.put("JBANG_CMD", jbang);
        environment.put("PATH", Path.of(jbang).toAbsolutePath().getParent() + File.pathSeparator
                + environment.getOrDefault("PATH", ""));
        workspace = Files.createTempDirectory("bob-airlock-").toRealPath();
        try {
            copyDemo();
        } catch (Exception failure) {
            close();
            throw failure;
        }
    }

    void copyDemo() throws Exception {
        Path demo = root.resolve("demo");
        try (var paths = Files.walk(demo)) {
            for (Path source : paths.toList()) {
                Path relative = demo.relativize(source);
                if (relative.startsWith(Path.of(".bob/state"))) {
                    continue;
                }
                Path destination = workspace.resolve(relative);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(source, destination);
                }
            }
        }
    }

    Result run(List<String> command, String input, int seconds) throws Exception {
        Path stdout = Files.createTempFile("airlock-stdout-", ".txt");
        Path stderr = Files.createTempFile("airlock-stderr-", ".txt");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(workspace.toFile())
                    .redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            builder.environment().putAll(environment);
            process = builder.start();
            try (var stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
            boolean completed = process.waitFor(seconds, TimeUnit.SECONDS);
            if (!completed) {
                terminate(process);
            }
            return new Result(completed ? process.exitValue() : 124,
                    Files.readString(stdout), Files.readString(stderr));
        } finally {
            if (process != null && process.isAlive()) {
                terminate(process);
            }
            Files.deleteIfExists(stdout);
            Files.deleteIfExists(stderr);
        }
    }

    static void terminate(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
    }

    Result script(String name, String input) throws Exception {
        return run(List.of(jbang, "--quiet", name), input, 60);
    }

    Result hook(String event, String command, String tool, String response, String call) throws Exception {
        var payload = JSON.createObjectNode().put("session_id", "lab-check")
                .put("cwd", workspace.toString()).put("hook_event_name", event)
                .put("tool_name", tool).put("tool_use_id", call).put("tool_response", response);
        payload.putObject("tool_input").put("command", command);
        return script(".bob/hooks/airlock-hook.java", JSON.writeValueAsString(payload));
    }

    Result hook(String event) throws Exception {
        return hook(event, "", "execute_command", "", "door-1");
    }

    void source(String relative) throws Exception {
        Files.copy(root.resolve(relative), workspace.resolve("src/Airlock.java"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    Map<String, String> controls() throws Exception {
        Map<String, String> hashes = new LinkedHashMap<>();
        try (var paths = Files.walk(workspace)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = workspace.relativize(path);
                if (!relative.equals(Path.of("src/Airlock.java")) && !relative.startsWith(Path.of(".bob/state"))) {
                    hashes.put(relative.toString(), hash(path));
                }
            }
        }
        return hashes;
    }

    List<Path> states() throws Exception {
        Path state = workspace.resolve(".bob/state");
        if (!Files.isDirectory(state)) {
            return List.of();
        }
        try (var paths = Files.list(state)) {
            return paths.filter(Files::isDirectory).sorted().toList();
        }
    }

    static List<String> actions(Path events) throws Exception {
        List<String> actions = new ArrayList<>();
        for (String line : Files.readAllLines(events)) {
            JsonNode event = JSON.readTree(line);
            actions.add(event.path("action").asText());
        }
        return actions;
    }

    static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    static String executable(String name) {
        for (String directory : System.getenv().getOrDefault("PATH", "").split(Pattern.quote(File.pathSeparator))) {
            Path candidate = Path.of(directory, name).toAbsolutePath();
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        Path fallback = Path.of(System.getProperty("user.home"), ".jbang/bin/jbang");
        if (name.equals("jbang") && Files.isExecutable(fallback)) {
            return fallback.toString();
        }
        throw new IllegalStateException("Cannot find " + name + " on PATH.");
    }

    static void report(Path destination, Object value) throws Exception {
        Files.createDirectories(destination.getParent());
        String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n";
        Files.writeString(destination, json);
        // The verification report is this CLI's output.
        System.out.print(json);
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @Override
    public void close() throws Exception {
        try (var paths = Files.walk(workspace)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    record Result(int exit, String stdout, String stderr) {
    }
}
