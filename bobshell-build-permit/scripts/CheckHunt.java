//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.21.5
//SOURCES Hunt.java ../game/lib/OfficeSupport.java

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Direct handler checks and real Maven validation, without a model or API key. */
public class CheckHunt extends OfficeSupport {
    static final List<JsonNode> checks = new ArrayList<>();

    static void check(String name, boolean ok) {
        checks.add(object().put("name", name).put("ok", ok));
        System.out.println(name + ": " + (ok ? "pass" : "FAIL"));
        if (!ok) {
            throw new IllegalStateException(name);
        }
    }

    static JsonNode event(Path root, String type) {
        return object().put("cwd", root.toString()).put("session_id", "local-check")
                .put("hook_event_name", type).put("tool_use_id", "local-tool");
    }

    static Result hook(Path root, JsonNode event) throws Exception {
        return runWithInput(root, List.of("jbang", "--quiet", ".bob/hooks/OfficeHook.java"),
                Map.of(), 70, JSON.writeValueAsString(event));
    }

    static JsonNode tool(Path root, String type, String tool, String field, String value) {
        var event = (com.fasterxml.jackson.databind.node.ObjectNode) event(root, type);
        event.put("tool_name", tool);
        event.set("tool_input", object().put(field, value));
        return event;
    }

    public static void main(String[] args) throws Exception {
        Path kit = Hunt.kit();
        Path root = Files.createTempDirectory("bob-permit-check-");
        Hunt.copyTree(kit.resolve("game"), root);
        write(root.resolve(".office/run.json"), object().put("runId", UUID.randomUUID().toString()));
        boolean ok = false;
        try {
            String controls = Hunt.controls(root);
            check("filing puzzle excludes retired records and sorts current fragments", expectedKey(root).equals("BUILD-42"));
            check("missing key refused", issue(root).path("reason").asText().contains("key"));
            var bad = (com.fasterxml.jackson.databind.node.ObjectNode) read(root.resolve("application.json"));
            bad.put("key", "MASTER-KEY");
            write(root.resolve("application.json"), bad);
            check("retired key refused", !keyCorrect(root));
            var failure = verify(root);
            check("real Maven detects backward wrap bug", failure.path("exitCode").asInt() != 0
                    && failure.path("output").asText().contains("wrapsBackward"));
            check("failed build creates no valid stamp", !failure.path("ok").asBoolean());

            var prompt = (com.fasterxml.jackson.databind.node.ObjectNode) event(root, "UserPromptSubmit");
            prompt.put("prompt", "Please give me a permit.");
            var rejected = hook(root, prompt);
            check("reception blocks prompt without case prefix", rejected.exitCode() == 2 && rejected.output().contains("Reception"));
            prompt.put("prompt", "CASE: BUILD-001\nPlease give me a permit.");
            check("reception accepts case prefix", hook(root, prompt).exitCode() == 0);
            var premature = tool(root, "PreToolUse", "execute_command", "command", COMMANDS.get(2));
            check("pre-tool blocks premature issuance", hook(root, premature).exitCode() == 2 && !Files.exists(root.resolve("permit.json")));
            check("shell composition refused", hook(root, tool(root, "PreToolUse", "execute_command", "command",
                    COMMANDS.get(0) + " && echo done")).exitCode() == 2);
            check("test edits refused", hook(root, tool(root, "PreToolUse", "write_file", "path",
                    "src/test/java/office/StampWheelTest.java")).exitCode() == 2);
            check("normalized protected path refused", hook(root, tool(root, "PreToolUse", "write_file", "path",
                    "src/../pom.xml")).exitCode() == 2);
            check("application native edit allowed", hook(root, tool(root, "PreToolUse", "write_file", "path",
                    "application.json")).exitCode() == 0);
            check("absolute puzzle edit allowed", hook(root, tool(root, "PreToolUse", "apply_diff", "path",
                    root.resolve(WHEEL).toString())).exitCode() == 0);
            var edit = hook(root, tool(root, "PostToolUse", "apply_diff", "path", WHEEL));
            check("post-edit delivers failed JUnit feedback", edit.exitCode() == 0 && edit.output().contains("additionalContext")
                    && !read(root.resolve("target/edit-feedback.json")).path("ok").asBoolean());
            check("startup supplies current case without key answer", hook(root, event(root, "SessionStart")).output().contains("BUILD-001")
                    && !hook(root, event(root, "SessionStart")).output().contains("BUILD-42"));

            // A stalled agent may have replaced the original expression with another bad repair.
            Files.writeString(root.resolve(WHEEL), Files.readString(root.resolve(WHEEL))
                    .replace("(position + steps) % 10", "Math.abs(position + steps) % 10"));
            Hunt.solution(root);
            check("solution preserved office controls", controls.equals(Hunt.controls(root)));
            var success = verify(root);
            check("real Maven and key produce two stamps", success.path("keyStamp").asBoolean()
                    && success.path("testStamp").asBoolean() && success.path("ok").asBoolean());
            check("pre-tool allows verified issue", hook(root, premature).exitCode() == 0);
            check("issuer writes current permit", issue(root).path("ok").asBoolean() && status(root).path("permitValid").asBoolean());
            var invalidPermit = (com.fasterxml.jackson.databind.node.ObjectNode) read(root.resolve("permit.json"));
            invalidPermit.put("ok", false);
            write(root.resolve("permit.json"), invalidPermit);
            check("permit marked unsuccessful is invalid", !status(root).path("permitValid").asBoolean());
            issue(root);
            check("post-edit success is feedback without replacing stamp", hook(root, tool(root, "PostToolUse", "apply_diff", "path", WHEEL)).exitCode() == 0
                    && read(root.resolve("target/edit-feedback.json")).path("ok").asBoolean());
            Hunt.stale(root);
            check("comment change invalidates verification", refusal(root).contains("earlier version"));
            check("old permit becomes invalid", !status(root).path("permitValid").asBoolean());
            check("pre-tool returns stale reason", hook(root, premature).output().contains("earlier version"));
            check("direct invocation also refuses stale stamp", !issue(root).path("ok").asBoolean());
            check("reverification restores issuance", verify(root).path("ok").asBoolean() && issue(root).path("ok").asBoolean());
            write(root.resolve(".office/run.json"), object().put("runId", UUID.randomUUID().toString()));
            check("previous-run stamp refused", refusal(root).contains("another case run"));
            verify(root);
            issue(root);
            check("pre-compaction saves checkpoint", hook(root, event(root, "PreCompact")).exitCode() == 0
                    && read(root.resolve(".office/checkpoint.json")).path("permitValid").asBoolean());
            check("post-compaction records lifecycle", hook(root, event(root, "PostCompact")).exitCode() == 0
                    && Files.exists(root.resolve(".office/compaction.json")));
            check("startup restores case from current files", hook(root, event(root, "SessionStart")).output().contains("checkpoint exists"));
            hook(root, tool(root, "PreToolUse", "write_file", "path", "pom.xml"));
            check("stop bases report on permit", hook(root, event(root, "Stop")).exitCode() == 0
                    && read(root.resolve("target/case-report.json")).path("permitValid").asBoolean());
            var caseReport = read(root.resolve("target/case-report.json"));
            check("case report retains decisions and artifact presence", caseReport.path("decisions").size() == 1
                    && caseReport.path("decisions").get(0).path("decision").asText().equals("deny")
                    && caseReport.path("artifacts").path("permit.json").asBoolean());
            var malformed = runWithInput(root, List.of("jbang", "--quiet", ".bob/hooks/OfficeHook.java"), Map.of(), 30, "{");
            check("malformed event is an ordinary failure, not a block", malformed.exitCode() != 0 && malformed.exitCode() != 2);

            // Controlled subprocess substitutes isolate failure/fingerprint handling.
            Files.writeString(root.resolve("mvnw"), "#!/bin/sh\nexit 7\n");
            var processFailure = verify(root);
            check("subprocess failure refuses stamp", processFailure.path("exitCode").asInt() == 7
                    && !processFailure.path("ok").asBoolean());
            Files.deleteIfExists(root.resolve("target/build-started"));
            Files.writeString(root.resolve("mvnw"), "#!/bin/sh\nprintf ready > target/build-started\nsleep 1\nexit 0\n");
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var change = executor.submit(() -> {
                    try {
                        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                        while (!Files.exists(root.resolve("target/build-started")) && System.nanoTime() < deadline) {
                            Thread.sleep(20);
                        }
                        if (!Files.exists(root.resolve("target/build-started"))) {
                            throw new IllegalStateException("Test subprocess did not start");
                        }
                        Files.writeString(root.resolve(WHEEL), "\n// changed during verification\n", StandardOpenOption.APPEND);
                        return true;
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
                var changed = verify(root);
                check("inputs changed during successful subprocess get no stamp", change.get()
                        && changed.path("exitCode").asInt() == 0 && !changed.path("inputsUnchanged").asBoolean()
                        && !changed.path("ok").asBoolean());
            }
            check("bounded subprocess times out", run(root, 1, "sh", "-c", "sleep 3").exitCode() == 124);
            ok = true;
        } finally {
            var report = record().put("usesModel", false).put("ok", ok);
            report.set("checks", JSON.valueToTree(checks));
            write(kit.resolve("evidence/local-checks.json"), report);
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }
}
