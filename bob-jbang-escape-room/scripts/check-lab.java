///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2
//SOURCES LabSupport.java

import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

class CheckLab {
    public static void main(String[] args) throws Exception {
        try (var lab = new LabSupport()) {
            var baseline = lab.script("verify.java", "");
            LabSupport.require(baseline.exit() == 1 && baseline.stdout().contains("3/7 passed; 4 failed"),
                    "Expected the broken baseline to fail four cases.");
            var empty = lab.script(".bob/hooks/airlock-hook.java", "");
            LabSupport.require(empty.exit() == 0 && empty.stdout().isEmpty() && empty.stderr().isEmpty(),
                    "Empty stdin should exit successfully without output.");
            var start = lab.hook("SessionStart");
            LabSupport.require(start.exit() == 0 && !context(start).isBlank(), "Missing session context.");
            var unrelated = lab.hook("PreToolUse", "printf 'HELLO\\n'", "execute_command", "", "other-1");
            LabSupport.require(unrelated.exit() == 0 && unrelated.stdout().isEmpty(),
                    "An unrelated command should pass through.");
            var locked = lab.hook("PreToolUse", LabSupport.OPEN, "execute_command", "", "door-1");
            LabSupport.require(locked.exit() == 2 && locked.stdout().isEmpty()
                    && locked.stderr().contains("AIRLOCK LOCKED"), "The broken interlock should block the door.");

            lab.source("solution/Airlock.java");
            var feedback = lab.hook("PostToolUse", "", "apply_diff", "", "edit-1");
            LabSupport.require(feedback.exit() == 0 && context(feedback).contains("7/7 passed"),
                    "Post-edit context should contain the passing result.");
            var cleared = lab.hook("PreToolUse", LabSupport.OPEN, "execute_command", "", "door-1");
            LabSupport.require(cleared.exit() == 0 && cleared.stdout().isEmpty() && cleared.stderr().isEmpty(),
                    "The repaired interlock should allow the door without output.");
            var opened = lab.hook("PostToolUse", LabSupport.OPEN, "execute_command", "AIRLOCK_OPEN", "door-1");
            LabSupport.require(opened.exit() == 0, "Recording the door result failed.");
            LabSupport.require(lab.hook("Stop").exit() == 0, "The final report failed.");

            var states = lab.states();
            LabSupport.require(states.size() == 1, "Expected one session state directory.");
            var state = states.getFirst();
            var actions = LabSupport.actions(state.resolve("events.jsonl"));
            LabSupport.require(actions.equals(List.of("ARRIVAL", "LOCKED", "GREEN", "CLEARED", "ESCAPED", "DEBRIEF")),
                    "Unexpected hook transitions: " + actions);
            byte[] png = Files.readAllBytes(state.resolve("replay.png"));
            byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            LabSupport.require(png.length >= 8 && Arrays.equals(Arrays.copyOf(png, 8), signature),
                    "The replay is missing a valid PNG signature.");
            LabSupport.require(Files.readString(state.resolve("replay.html")).contains("Escaped. Final verification"),
                    "The HTML replay should report a successful escape.");

            lab.source("demo/src/Airlock.java");
            LabSupport.require(lab.hook("Stop").exit() == 0, "The stale-source report failed.");
            LabSupport.require(Files.readString(state.resolve("replay.html")).contains("Mission incomplete."),
                    "Changing the source should invalidate the final success report.");
            var relocked = lab.hook("PreToolUse", LabSupport.OPEN, "execute_command", "", "door-2");
            LabSupport.require(relocked.exit() == 2, "A later failing check should block another opening.");

            var report = LabSupport.JSON.createObjectNode().put("baseline", "3/7 passed; 4 failed")
                    .put("solution", "7/7 passed; 0 failed");
            report.set("transitions", LabSupport.JSON.valueToTree(actions));
            report.put("empty_stdin", "pass").put("unrelated_command", "pass")
                    .put("stale_source_rejected", true).put("replay_png", "valid PNG signature");
            LabSupport.report(lab.root.resolve("evidence/lab-checks.json"), report);
        }
    }

    static String context(LabSupport.Result result) throws Exception {
        return LabSupport.JSON.readTree(result.stdout()).path("hookSpecificOutput").path("additionalContext").asText();
    }
}
