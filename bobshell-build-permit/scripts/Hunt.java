//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.21.5
//SOURCES ../game/lib/OfficeSupport.java

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Human-operated launcher; Bob never needs this file or its credential input. */
public class Hunt extends OfficeSupport {
    static Path kit() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("game/pom.xml"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalArgumentException("Run from bobshell-build-permit");
        }
        return root;
    }

    static String option(List<String> args, String name) {
        int index = args.indexOf(name);
        if (index < 0) {
            return null;
        }
        if (index + 1 >= args.size()) {
            throw new IllegalArgumentException("Missing value for " + name);
        }
        return args.get(index + 1);
    }

    static void copyTree(Path source, Path destination) throws Exception {
        try (var files = Files.walk(source)) {
            for (Path file : files.toList()) {
                if (source.relativize(file).startsWith("target")) {
                    continue;
                }
                Path target = destination.resolve(source.relativize(file));
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static void init(Path kit, Path root, boolean reset) throws Exception {
        if (Files.exists(root)) {
            if (!reset) {
                throw new IllegalStateException("Workspace already exists. Use reset to discard this game run.");
            }
            if (!Files.exists(root.resolve(".office/run.json"))) {
                throw new IllegalStateException("Refusing to reset a directory without the office run marker.");
            }
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
        copyTree(kit.resolve("game"), root);
        write(root.resolve(".office/run.json"), object().put("caseId", CASE)
                .put("runId", UUID.randomUUID().toString()));
        for (String source : List.of("tools/Office.java", ".bob/hooks/OfficeHook.java")) {
            var result = run(root, 120, "jbang", "build", source);
            if (result.exitCode() != 0) {
                throw new IllegalStateException("JBang warmup failed: " + result.output());
            }
        }
        var build = run(root, 120, "./mvnw", "test", "-DskipTests", "-q");
        if (build.exitCode() != 0) {
            throw new IllegalStateException("Initial compilation failed: " + build.output());
        }
        System.out.println("Office initialized. The backward-wrap test is intentionally failing.");
    }

    static String controls(Path root) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = root.relativize(file).toString();
                if (name.startsWith("target/") || name.startsWith(".office/")
                        || name.equals("permit.json") || name.equals("application.json") || name.equals(WHEEL)) {
                    continue;
                }
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(file));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static void solution(Path root) throws Exception {
        var form = object().put("caseId", CASE).put("key", expectedKey(root));
        write(root.resolve("application.json"), form);
        Path source = root.resolve(WHEEL);
        Files.writeString(source, """
                package office;

                public final class StampWheel {
                    private StampWheel() {
                    }

                    public static int rotate(int position, int steps) {
                        return Math.floorMod(position + steps, 10);
                    }
                }
                """);
        System.out.println("Solution applied. Run verify and issue from workspace.");
    }

    static void stale(Path root) throws Exception {
        if (!status(root).path("ready").asBoolean()) {
            throw new IllegalStateException("Obtain a successful verification before the stale-stamp experiment.");
        }
        Files.writeString(root.resolve(WHEEL), "\n// The application changed after the test stamp.\n",
                java.nio.file.StandardOpenOption.APPEND);
        System.out.println("Added a harmless source comment. The test stamp now refers to older files.");
    }

    static int launch(Path kit, Path root, List<String> args, boolean chat) throws Exception {
        runId(root);
        String filename = option(args, "--key-file");
        String credential = filename == null ? System.getenv("BOB_API_KEY")
                : read(Path.of(filename)).path("apikey").asText();
        if (credential == null || credential.isBlank()) {
            throw new IllegalArgumentException("Supply --key-file /absolute/path/key.json or BOB_API_KEY");
        }
        var command = new ArrayList<>(List.of("bob", chat ? "chat" : "run", "--workspace", root.toString(),
                "--disable-mcp", "--disable-subagents", "--trust", "--max-cost", "2", "--max-turns", "35"));
        String resume = option(args, "--resume");
        if (resume != null) {
            command.addAll(List.of("--resume", resume));
        }
        var environment = Map.of("BOB_API_KEY", credential, "BOBSHELL_API_KEY", "");
        if (chat) {
            var builder = new ProcessBuilder(command).directory(root.toFile()).inheritIO();
            builder.environment().putAll(environment);
            Process process = builder.start();
            Thread shutdown = new Thread(() -> terminate(process));
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                return process.waitFor();
            } finally {
                Runtime.getRuntime().removeShutdownHook(shutdown);
            }
        }
        String name = option(args, "--prompt");
        if (name == null) {
            name = "mission";
        }
        if (!List.of("mission", "reception-block", "reception-allow", "stale", "recover", "feedback")
                .contains(name)) {
            throw new IllegalArgumentException("Unknown prompt name");
        }
        command.addAll(List.of("--format", "json", Files.readString(kit.resolve("prompts/" + name + ".txt"))));
        String before = controls(root);
        var result = run(root, command, environment, 420);
        String response = result.output().replace(credential, "[REDACTED]");
        Files.createDirectories(kit.resolve("evidence"));
        Files.writeString(kit.resolve("evidence/" + name + "-raw.json"), response);
        JsonNode parsed;
        try {
            parsed = JSON.readTree(response);
        } catch (Exception exception) {
            parsed = object();
        }
        var receipt = record().put("prompt", name).put("exitCode", result.exitCode())
                .put("runId", runId(root)).put("controlsUnchanged", before.equals(controls(root)))
                .put("taskId", parsed.path("stats").path("task_id").asText())
                .put("toolCalls", parsed.path("stats").path("tool_calls").asInt())
                .put("cost", parsed.path("stats").path("session_costs").asDouble());
        receipt.set("case", status(root));
        write(kit.resolve("evidence/" + name + "-latest.json"), receipt);
        output(receipt);
        if (!receipt.path("controlsUnchanged").asBoolean()) {
            throw new IllegalStateException("Game controls changed during Bob's run");
        }
        return result.exitCode();
    }

    public static void main(String[] input) throws Exception {
        if (input.length == 0) {
            System.out.println("Usage: Hunt.java init|reset|play|chat|solution|stale|status|events [--key-file FILE] [--resume ID]");
            return;
        }
        Path kit = kit();
        Path root = kit.resolve("workspace");
        var args = Arrays.asList(input).subList(1, input.length);
        int exit = 0;
        switch (input[0]) {
            case "init" -> init(kit, root, false);
            case "reset" -> init(kit, root, true);
            case "play" -> exit = launch(kit, root, args, false);
            case "chat" -> exit = launch(kit, root, args, true);
            case "solution" -> solution(root);
            case "stale" -> stale(root);
            case "status" -> output(status(root));
            case "events" -> System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(events(root)));
            default -> throw new IllegalArgumentException("Unknown launcher command");
        }
        if (exit != 0) {
            System.exit(exit);
        }
    }
}
