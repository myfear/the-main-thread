//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.21.5
//SOURCES ../../lib/OfficeSupport.java

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class OfficeHook extends OfficeSupport {
    static JsonNode readEvent() throws IOException {
        byte[] bytes = System.in.readNBytes(1024 * 1024 + 1);
        if (bytes.length > 1024 * 1024) {
            throw new IOException("Hook payload exceeds 1 MiB");
        }
        JsonNode event = JSON.readTree(bytes);
        if (event == null || !event.isObject() || !event.has("hook_event_name")) {
            throw new IOException("Expected a Bobshell hook event object");
        }
        return event;
    }

    static String gate(Path root, JsonNode event) throws Exception {
        String tool = event.path("tool_name").asText();
        JsonNode input = event.path("tool_input");
        if (tool.equals("execute_command")) {
            String command = input.path("command").asText();
            if (!COMMANDS.contains(command)) {
                return "Counter closed: use exactly one of the three commands in START_HERE.md.";
            }
            if (input.path("is_background").asBoolean() || input.path("background").asBoolean()) {
                return "The office processes applications in the foreground.";
            }
            String directory = input.path("cwd").asText("");
            if (!directory.isBlank() && !root.equals(root.resolve(directory).normalize().toAbsolutePath())) {
                return "Submit from the office workspace root.";
            }
            if (command.equals(COMMANDS.get(2))) {
                return refusal(root);
            }
        } else if (List.of("write_file", "apply_diff", "search_and_replace", "insert_content")
                .contains(tool)) {
            String name = input.path("path").asText(input.path("file_path").asText(""));
            Path file = root.resolve(name).normalize().toAbsolutePath();
            if (!file.equals(root.resolve(WHEEL)) && !file.equals(root.resolve("application.json"))) {
                return "That file belongs to the office. Edit only the application and StampWheel.java.";
            }
        } else if (!List.of("read_file", "list_files", "search_files", "codebase_search").contains(tool)) {
            return "This counter accepts repository reads, the two native edits, and office commands.";
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        JsonNode event = readEvent();
        Path root = Path.of(event.path("cwd").asText()).toAbsolutePath().normalize();
        String type = event.path("hook_event_name").asText();
        switch (type) {
            case "SessionStart" -> {
                audit(root, event, "briefing", null);
                System.out.println("Office for Verified Builds | CASE: " + CASE);
                System.out.println("Authorized game: read START_HERE.md and follow reception's filing clues.");
                System.out.println("Edit only application.json and " + WHEEL + ". Keep controls and tests unchanged.");
                System.out.println("Use the three exact foreground commands in START_HERE.md. Native edits get test feedback.");
                System.out.println("Current case: " + JSON.writeValueAsString(status(root)));
                if (Files.exists(root.resolve(".office/checkpoint.json"))) {
                    System.out.println("A checkpoint exists. The current files and status above take precedence.");
                }
            }
            case "UserPromptSubmit" -> {
                if (!event.path("prompt").asText().stripLeading().startsWith("CASE: " + CASE)) {
                    String reason = "Reception requires CASE: " + CASE + " at the start of your prompt.";
                    audit(root, event, "deny", reason);
                    System.err.println(reason);
                    System.exit(2);
                }
                audit(root, event, "allow", null);
            }
            case "PreToolUse" -> {
                String reason = gate(root, event);
                audit(root, event, reason == null ? "allow" : "deny", reason);
                if (reason != null) {
                    System.err.println(reason);
                    System.exit(2);
                }
            }
            case "PostToolUse" -> {
                audit(root, event, "completed", null);
                String tool = event.path("tool_name").asText();
                if (List.of("write_file", "apply_diff", "search_and_replace", "insert_content").contains(tool)) {
                    var build = run(root, 50, "./mvnw", "test", "-Dtest=StampWheelTest", "-q");
                    var report = record().put("runId", runId(root)).put("exitCode", build.exitCode())
                            .put("ok", build.exitCode() == 0).put("output", tail(build.output()));
                    write(root.resolve("target/edit-feedback.json"), report);
                    var response = object();
                    response.set("hookSpecificOutput", object().put("hookEventName", "PostToolUse")
                            .put("additionalContext", "Test department: " + JSON.writeValueAsString(report)
                                    + "\nThese are edit checks. Run verify to obtain the two official stamps."));
                    output(response);
                }
            }
            case "PreCompact" -> {
                audit(root, event, "checkpoint", null);
                var checkpoint = status(root).put("fingerprint", fingerprint(root));
                write(root.resolve(".office/checkpoint.json"), checkpoint);
            }
            case "PostCompact" -> {
                audit(root, event, "compacted", null);
                // Keep the lifecycle fact, not the full conversation summary.
                write(root.resolve(".office/compaction.json"), record().put("runId", runId(root))
                        .put("session", event.path("session_id").asText())
                        .put("trigger", event.path("trigger").asText()));
            }
            case "Stop" -> {
                audit(root, event, "report", null);
                var report = status(root).put("session", event.path("session_id").asText())
                        .put("fingerprint", fingerprint(root));
                String run = runId(root);
                report.set("decisions", JSON.valueToTree(events(root).stream()
                        .filter(row -> run.equals(row.path("runId").asText())
                                && row.path("event").asText().equals("PreToolUse"))
                        .toList()));
                var artifacts = object();
                for (String name : List.of("target/verification.json", "target/edit-feedback.json", "permit.json",
                        ".office/checkpoint.json", ".office/compaction.json")) {
                    artifacts.put(name, Files.exists(root.resolve(name)));
                }
                report.set("artifacts", artifacts);
                write(root.resolve("target/case-report.json"), report);
            }
            default -> throw new IllegalArgumentException("Unexpected hook event: " + type);
        }
    }
}
