//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.21.5
//SOURCES Hunt.java ../game/lib/OfficeSupport.java

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fresh model runs, checked against events, controls, stamps and permits. */
public class CheckBob extends OfficeSupport {
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[0].equals("--key-file")) {
            throw new IllegalArgumentException("Usage: CheckBob.java --key-file /absolute/path/key.json");
        }
        Path kit = Hunt.kit();
        Path root = kit.resolve("workspace");
        var results = JSON.createArrayNode();
        boolean ok = true;
        for (int round = 1; round <= 2; round++) {
            Hunt.init(kit, root, Files.exists(root));
            var options = List.of("--key-file", args[1]);
            if (round == 1) {
                int blocked = Hunt.launch(kit, root, List.of("--key-file", args[1], "--prompt", "reception-block"), false);
                boolean reception = blocked == 1
                        && Files.readString(kit.resolve("evidence/reception-block-raw.json")).contains("Prompt blocked by hook");
                results.add(object().put("name", "reception-block").put("ok", reception));
                ok &= reception;
                int allowed = Hunt.launch(kit, root, List.of("--key-file", args[1], "--prompt", "reception-allow"), false);
                var briefing = read(kit.resolve("evidence/reception-allow-latest.json"));
                boolean startup = allowed == 0 && briefing.path("toolCalls").asInt() == 0;
                results.add(object().put("name", "startup-and-reception").put("ok", startup));
                ok &= startup;
                int feedbackExit = Hunt.launch(kit, root, List.of("--key-file", args[1], "--prompt", "feedback"), false);
                boolean feedback = feedbackExit == 0 && !read(root.resolve("target/edit-feedback.json")).path("ok").asBoolean()
                        && Files.readString(kit.resolve("evidence/feedback-raw.json")).contains("wrapsBackward");
                results.add(object().put("name", "live-failed-test-feedback").put("ok", feedback));
                ok &= feedback;
                Hunt.init(kit, root, true);
            }
            int exit = Hunt.launch(kit, root, options, false);
            var receipt = read(kit.resolve("evidence/mission-latest.json"));
            String session = receipt.path("taskId").asText();
            var entries = events(root).stream().filter(row -> row.path("session").asText().equals(session)).toList();
            var firstIssue = entries.stream().filter(row -> row.path("event").asText().equals("PreToolUse")
                    && row.path("command").asText().equals(COMMANDS.get(2))).findFirst();
            boolean deniedFirst = firstIssue.isPresent() && firstIssue.get().path("decision").asText().equals("deny");
            boolean completedIssue = entries.stream().anyMatch(row -> row.path("event").asText().equals("PostToolUse")
                    && row.path("command").asText().equals(COMMANDS.get(2)));
            boolean nativeEdits = entries.stream().anyMatch(row -> row.path("event").asText().equals("PostToolUse")
                    && List.of("write_file", "apply_diff", "search_and_replace", "insert_content").contains(row.path("tool").asText()));
            boolean permit = status(root).path("permitValid").asBoolean();
            boolean report = Files.exists(root.resolve("target/case-report.json"))
                    && read(root.resolve("target/case-report.json")).path("permitValid").asBoolean()
                    && !read(root.resolve("target/case-report.json")).path("decisions").isEmpty()
                    && read(root.resolve("target/case-report.json")).path("artifacts").path("permit.json").asBoolean();
            boolean passed = exit == 0 && deniedFirst && completedIssue && nativeEdits && permit && report
                    && receipt.path("controlsUnchanged").asBoolean();
            var result = object().put("name", "fresh-hunt-" + round).put("ok", passed)
                    .put("firstIssueDenied", deniedFirst).put("completedIssue", completedIssue)
                    .put("nativeEdits", nativeEdits).put("permitValid", permit).put("stopReportValid", report);
            result.set("run", receipt);
            results.add(result);
            write(kit.resolve("evidence/hunt-" + round + ".json"), result);
            write(kit.resolve("evidence/hunt-" + round + "-events.json"), JSON.valueToTree(entries));
            var artifacts = object();
            artifacts.set("verification", read(root.resolve("target/verification.json")));
            artifacts.set("permit", read(root.resolve("permit.json")));
            artifacts.set("caseReport", read(root.resolve("target/case-report.json")));
            write(kit.resolve("evidence/hunt-" + round + "-artifacts.json"), artifacts);
            ok &= passed;
            System.out.println("Fresh hunt " + round + ": " + (passed ? "pass" : "FAIL"));
            if (!passed) {
                break;
            }
            if (round == 2) {
                Hunt.stale(root);
                int staleExit = Hunt.launch(kit, root, List.of("--key-file", args[1], "--prompt", "stale", "--resume", session), false);
                var staleReceipt = read(kit.resolve("evidence/stale-latest.json"));
                var staleEvents = events(root).stream().filter(row -> row.path("session").asText().equals(session))
                        .filter(row -> row.path("at").asText().compareTo(receipt.path("at").asText()) > 0).toList();
                boolean denied = staleEvents.stream().anyMatch(row -> row.path("decision").asText().equals("deny")
                        && row.path("reason").asText().contains("earlier version"));
                boolean noIssue = staleEvents.stream().noneMatch(row -> row.path("event").asText().equals("PostToolUse")
                        && row.path("command").asText().equals(COMMANDS.get(2)));
                boolean staleOk = staleExit == 0 && denied && noIssue && !staleReceipt.path("case").path("permitValid").asBoolean();
                results.add(object().put("name", "resumed-stale-stamp-refusal").put("ok", staleOk));
                write(kit.resolve("evidence/stale-events.json"), JSON.valueToTree(staleEvents));
                ok &= staleOk;
                int recovered = Hunt.launch(kit, root, List.of("--key-file", args[1], "--prompt", "recover", "--resume", session), false);
                boolean recoveredOk = recovered == 0 && status(root).path("permitValid").asBoolean();
                results.add(object().put("name", "resumed-reverification-and-permit").put("ok", recoveredOk));
                ok &= recoveredOk;
            }
        }
        var report = record().put("usesModel", true).put("bobshell", "2.0.5").put("ok", ok);
        report.set("checks", results);
        write(kit.resolve("evidence/bob-checks.json"), report);
        if (!ok) {
            throw new IllegalStateException("Live validation failed; inspect evidence/bob-checks.json and ignored raw results.");
        }
    }
}
