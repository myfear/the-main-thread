///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2
//SOURCES Replay.java

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

class AirlockHook {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String OPEN = "printf 'AIRLOCK_OPEN\\n'";
    final Path state;
    final JsonNode event;

    AirlockHook(JsonNode event) throws Exception {
        this.event = event;
        String session = event.path("session_id").asText("manual");
        state = Path.of(".bob/state", hash(session.getBytes(StandardCharsets.UTF_8)));
        Files.createDirectories(state);
    }

    public static void main(String[] args) throws Exception {
        String raw = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        if (raw.isBlank()) {
            return;
        }
        JsonNode event = JSON.readTree(raw);
        try {
            new AirlockHook(event).handle();
        } catch (Exception failure) {
            // stderr and exit codes are part of Bob's hook protocol.
            System.err.print("Airlock hook could not complete: " + failure.getMessage());
            System.exit(event.path("hook_event_name").asText().equals("PreToolUse") ? 2 : 1);
        }
    }

    void handle() throws Exception {
        String name = event.path("hook_event_name").asText();
        String tool = event.path("tool_name").asText();
        String command = event.path("tool_input").path("command").asText();
        switch (name) {
            case "SessionStart" -> {
                record("ARRIVAL", "The outer door is locked. Repair the Java interlock to leave.");
                context(name, "Airlock lab: only edit src/Airlock.java. Opening requires the inner door "
                        + "to be sealed and pressure from 0 through 5 kPa, inclusive. "
                        + "Run jbang --quiet verify.java to check all seven cases.");
            }
            case "PreToolUse" -> {
                if (tool.equals("execute_command") && command.equals(OPEN)) {
                    var check = verify();
                    if (!check.passed()) {
                        record("LOCKED", check.output());
                        System.err.print("AIRLOCK LOCKED. Repair src/Airlock.java before trying the door again.\n"
                                + check.output());
                        System.exit(2);
                    }
                    ObjectNode approval = JSON.createObjectNode()
                            .put("tool_use_id", event.path("tool_use_id").asText())
                            .put("source_hash", sourceHash());
                    Files.writeString(state.resolve("gate.json"), JSON.writeValueAsString(approval));
                    record("CLEARED", "Fresh verification: " + check.firstLine());
                }
            }
            case "PostToolUse" -> {
                if (tool.equals("execute_command") && command.equals(OPEN)) {
                    Path gate = state.resolve("gate.json");
                    if (Files.exists(gate)) {
                        JsonNode approval = JSON.readTree(Files.readString(gate));
                        boolean sameCall = approval.path("tool_use_id").asText()
                                .equals(event.path("tool_use_id").asText());
                        boolean sameSource = approval.path("source_hash").asText().equals(sourceHash());
                        boolean opened = event.path("tool_response").asText().strip().equals("AIRLOCK_OPEN");
                        if (sameCall && sameSource && opened) {
                            record("ESCAPED", "The allowed tool call returned AIRLOCK_OPEN.");
                            Files.writeString(state.resolve("escaped"), sourceHash());
                        }
                    }
                } else if (!tool.equals("execute_command")) {
                    var check = verify();
                    record(check.passed() ? "GREEN" : "STILL LOCKED", check.output());
                    context(name, check.output());
                }
            }
            case "Stop" -> {
                var check = verify();
                boolean escaped = Files.exists(state.resolve("escaped"))
                        && Files.readString(state.resolve("escaped")).equals(sourceHash()) && check.passed();
                record("DEBRIEF", escaped ? "Escaped. Final verification: " + check.firstLine()
                        : "Door opening not verified for the final source. " + check.firstLine());
                Replay.render(state, escaped);
            }
            default -> {
            }
        }
    }

    Check verify() throws Exception {
        Path output = Files.createTempFile(state, "check-", ".txt");
        String jbang = System.getenv().getOrDefault("JBANG_CMD", "jbang");
        Process process = new ProcessBuilder(jbang, "--quiet", "verify.java")
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        process.getOutputStream().close();
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            return new Check(false, "Verifier exceeded 15 seconds.");
        }
        String result;
        try (var stream = Files.newInputStream(output)) {
            result = new String(stream.readNBytes(6000), StandardCharsets.UTF_8).strip();
        }
        Files.deleteIfExists(output);
        return new Check(process.exitValue() == 0, result);
    }

    void record(String action, String detail) throws Exception {
        ObjectNode row = JSON.createObjectNode().put("time", Instant.now().toString())
                .put("action", action).put("detail", detail)
                .put("tool_use_id", event.path("tool_use_id").asText());
        Files.writeString(state.resolve("events.jsonl"), JSON.writeValueAsString(row) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static void context(String event, String message) throws Exception {
        ObjectNode response = JSON.createObjectNode();
        response.putObject("hookSpecificOutput").put("hookEventName", event).put("additionalContext", message);
        System.out.print(JSON.writeValueAsString(response));
    }

    static String sourceHash() throws Exception {
        return hash(Files.readAllBytes(Path.of("src/Airlock.java")));
    }

    static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    record Check(boolean passed, String output) {
        String firstLine() {
            return output.lines().findFirst().orElse("Verifier produced no output.");
        }
    }
}
