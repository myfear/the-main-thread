///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS com.fasterxml.jackson.core:jackson-databind:2.18.2
//SOURCES LabSupport.java

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class RunBob {
    public static void main(String[] args) throws Exception {
        Path keyFile = null;
        Path output = Path.of("evidence/bob-run");
        for (int index = 0; index < args.length; index++) {
            String option = args[index];
            if (option.equals("--help")) {
                // Usage and the final verification report are this CLI's output.
                System.out.println("jbang --quiet scripts/run-bob.java [--key-file PATH] [--output NEW_DIRECTORY]");
                return;
            }
            LabSupport.require(option.equals("--key-file") || option.equals("--output"), "Unknown option: " + option);
            LabSupport.require(index + 1 < args.length, "Missing value for " + option);
            Path value = Path.of(args[++index]);
            if (option.equals("--key-file")) {
                keyFile = value;
            } else {
                output = value;
            }
        }
        String key = keyFile == null ? System.getenv("BOB_API_KEY")
                : LabSupport.JSON.readTree(Files.readString(keyFile)).path("apikey").asText();
        LabSupport.require(key != null && !key.isBlank(),
                "Set BOB_API_KEY or supply --key-file with an inference key JSON outside the repository.");
        output = output.toAbsolutePath().normalize();
        LabSupport.require(!Files.exists(output), "Choose a new output directory; existing evidence will not be overwritten.");
        String bob = LabSupport.executable("bob");

        try (var lab = new LabSupport()) {
            lab.environment.put("BOB_API_KEY", key);
            Files.createDirectories(output.getParent());
            Files.createDirectory(output);
            var redactor = new Redactor(key, lab.workspace.toString(), System.getProperty("user.home"));
            var controls = lab.controls();
            var baseline = lab.script("verify.java", "");
            save(output.resolve("baseline.txt"), baseline, redactor);
            LabSupport.require(baseline.exit() == 1 && baseline.stdout().contains("3/7 passed; 4 failed"),
                    "Expected the broken baseline to fail four cases. See baseline.txt.");
            var warm = lab.script(".bob/hooks/airlock-hook.java", "");
            LabSupport.require(warm.exit() == 0, "Hook warmup failed.");

            long start = System.nanoTime();
            var run = lab.run(List.of(bob, "run", "--workspace", lab.workspace.toString(), "--trust", "--accept-license",
                    "--disable-mcp", "--disable-subagents", "--max-turns", "12", "--max-cost", "1",
                    "--format", "stream-json", Files.readString(lab.workspace.resolve("prompt.txt"))), "", 240);
            Files.writeString(output.resolve("bob.jsonl"), redactor.clean(run.stdout()));
            Files.writeString(output.resolve("stderr.txt"), redactor.clean(run.stderr()));
            var finalTests = lab.script("verify.java", "");
            save(output.resolve("final-tests.txt"), finalTests, redactor);
            Path source = lab.workspace.resolve("src/Airlock.java");
            if (Files.isRegularFile(source)) {
                Files.writeString(output.resolve("Airlock.java"), redactor.clean(Files.readString(source)));
            }
            var states = lab.states();
            List<String> actions = List.of();
            if (states.size() == 1) {
                Path state = states.getFirst();
                for (String name : List.of("events.jsonl", "replay.html", "replay.png")) {
                    Path path = state.resolve(name);
                    if (Files.isRegularFile(path)) {
                        if (name.endsWith(".png")) {
                            Files.copy(path, output.resolve(name));
                        } else {
                            Files.writeString(output.resolve(name), redactor.clean(Files.readString(path)));
                        }
                    }
                }
                if (Files.isRegularFile(state.resolve("events.jsonl"))) {
                    actions = LabSupport.actions(state.resolve("events.jsonl"));
                }
            }
            boolean unchanged = controls.equals(lab.controls());
            boolean replay = Files.isRegularFile(output.resolve("replay.png"));
            var summary = LabSupport.JSON.createObjectNode().put("bob_exit", run.exit())
                    .put("seconds", Math.round((System.nanoTime() - start) / 10_000_000.0) / 100.0)
                    .put("final_tests_exit", finalTests.exit()).put("controls_unchanged", unchanged)
                    .put("replay_written", replay)
                    .put("bob_version", lab.run(List.of(bob, "--version"), "", 30).stdout().strip())
                    .put("jbang_version", lab.run(List.of(lab.jbang, "version"), "", 30).stdout().strip());
            summary.set("actions", LabSupport.JSON.valueToTree(actions));
            LabSupport.report(output.resolve("summary.json"), summary);
            LabSupport.require(run.exit() == 0 && finalTests.exit() == 0 && unchanged && replay
                    && actions.contains("LOCKED") && actions.contains("ESCAPED")
                    && actions.indexOf("LOCKED") < actions.indexOf("ESCAPED"),
                    "The Bob run did not complete the expected escape. Inspect the saved evidence.");
        }
    }

    static void save(Path path, LabSupport.Result result, Redactor redactor) throws Exception {
        Files.writeString(path, redactor.clean(result.stdout() + result.stderr()));
    }

    record Redactor(String key, String workspace, String home) {
        String clean(String text) {
            return text.replace(key, "[REDACTED]").replace(workspace, "<workspace>").replace(home, "<home>")
                    .replaceAll("(call_[A-Za-z0-9_-]+)__thought__[A-Za-z0-9+/=_-]+", "$1__opaque_signature_omitted");
        }
    }
}
