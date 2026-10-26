package com.themainthread.vex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@TopCommand
@Command(name = "vex-investigator", mixinStandardHelpOptions = true, subcommands = {
        VexCli.Doctor.class, VexCli.Prepare.class, VexCli.Verify.class, VexCli.Approve.class,
        VexCli.Export.class, VexCli.TestDemo.class, VexCli.HuginnCheck.class, VexCli.ValidateVex.class })
public class VexCli implements Runnable {
    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    abstract static class Operation implements Callable<Integer> {
        @Spec
        CommandSpec spec;

        abstract int execute() throws Exception;

        @Override
        public Integer call() {
            try {
                return execute();
            } catch (CaseException failure) {
                spec.commandLine().getErr().println(failure.getMessage());
                return failure.code;
            } catch (Exception failure) {
                spec.commandLine().getErr().println(failure.getClass().getSimpleName() + ": " + failure.getMessage());
                return 4;
            }
        }
    }

    @Command(name = "doctor", description = "Check local tools; never starts Bob or queries OSV")
    static class Doctor extends Operation {
        @Override
        int execute() throws Exception {
            ObjectNode report = toolReport();
            spec.commandLine().getOut().println(report.toPrettyString());
            return requiredToolsPresent(report.path("tools")) ? 0 : 2;
        }

        private ObjectNode toolReport() throws InterruptedException {
            var report = EvidenceHasher.object();
            report.put("java", System.getProperty("java.version"));
            report.put("java_required", "25+");
            report.put("maven_wrapper", Files.isRegularFile(Path.of("mvnw")) ? "present (required to build)" : "missing");
            var tools = report.putObject("tools");
            for (String tool : List.of("git", "bob", "jbang", "vexctl")) {
                tools.put(tool, probeVersion(tool));
            }
            report.put("huginn", Files.isRegularFile(Path.of(".bob/mcp.json"))
                    ? "configured; use huginn-check --project sample-app for MCP handshake" : "unconfigured (optional for offline)");
            report.put("pilot", "optional; explicit diagnostic goals only; not invoked");
            return report;
        }

        private String probeVersion(String tool) throws InterruptedException {
            String argument = tool.equals("vexctl") ? "version" : "--version";
            try {
                Process process = new ProcessBuilder(tool, argument).redirectErrorStream(true).start();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return "timeout";
                }
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            } catch (IOException failure) {
                return "unavailable";
            }
        }

        private boolean requiredToolsPresent(JsonNode tools) {
            return Runtime.version().feature() >= 25 && !tools.path("git").asText().equals("unavailable");
        }
    }

    @Command(name = "prepare", description = "Freeze an already-built JVM Fast JAR and query OSV")
    static class Prepare extends Operation {
        @Option(names = "--project", required = true)
        Path project;

        @Option(names = "--sbom", required = true)
        Path sbom;

        @Option(names = "--dependency-sbom")
        Path dependencySbom;

        @Option(names = "--out", required = true)
        Path output;

        @Option(names = "--osv-base-url", defaultValue = "https://api.osv.dev")
        String base;

        @Option(names = "--product-id")
        String product;

        @Option(names = "--test-only")
        boolean testOnly;

        @Option(names = "--usage-report")
        Path usageReport;

        @Option(names = "--pilot-audit")
        Path auditReport;

        @Override
        int execute() throws Exception {
            Path directory = output.toAbsolutePath().normalize();
            var manifest = new CasePreparer().prepare(project, sbom, dependencySbom, directory,
                    base, product, testOnly, Duration.ofSeconds(10), usageReport, auditReport);
            spec.commandLine().getOut().println("Prepared " + manifest.path("case_id").asText()
                    + "; OSV complete=" + manifest.path("osv_complete"));
            return manifest.path("osv_complete").asBoolean() ? 0 : 3;
        }
    }

    abstract static class CaseOperation extends Operation {
        @Option(names = "--case", required = true)
        Path root;
    }

    @Command(name = "verify", description = "Independently check inputs, proposal and status preconditions")
    static class Verify extends CaseOperation {
        @Override
        int execute() throws Exception {
            var report = new AssessmentVerifier().verify(root);
            EvidenceHasher.write(EvidenceHasher.safe(root, "verify-report.json"), report);
            spec.commandLine().getOut().println(report.toPrettyString());
            return report.path("exit_code").asInt();
        }
    }

    @Command(name = "approve", description = "Interactive human review; no unattended approval flags")
    static class Approve extends CaseOperation {
        @Override
        int execute() throws Exception {
            new ApprovalService().approve(root);
            return 0;
        }
    }

    @Command(name = "export", description = "Recheck evidence and human review; write OpenVEX 0.2.0 offline")
    static class Export extends CaseOperation {
        @Override
        int execute() throws Exception {
            new OpenVexWriter().export(root);
            spec.commandLine().getOut().println("Wrote " + root.resolve("vex.json"));
            return 0;
        }
    }

    @Command(name = "test-demo", description = "TEST_ONLY local stub, synthetic proposal and simulated review")
    static class TestDemo extends Operation {
        @Option(names = "--out", required = true)
        Path output;

        @Option(names = "--project", defaultValue = "sample-app")
        Path project;

        @Option(names = "--failure", defaultValue = "none", description = "TEST_ONLY injection: none, outage, tamper, unused")
        String failure;

        @Override
        int execute() throws Exception {
            checkFailureInjection();
            OfflineDemo.run(project, output.toAbsolutePath().normalize(), failure);
            spec.commandLine().getOut().println("TEST_ONLY fixture exported to " + output
                    + "; no Bob or real human review was used");
            return 0;
        }

        private void checkFailureInjection() {
            if (!List.of("none", "outage", "tamper", "unused").contains(failure)) {
                throw new CaseException(2, "Unknown failure injection");
            }
        }
    }

    @Command(name = "huginn-check", description = "Optional STDIO MCP handshake and read-only tool smoke test")
    static class HuginnCheck extends Operation {
        @Option(names = "--project", defaultValue = "sample-app")
        Path project;

        @Option(names = "--server-jar", description = "Use a previously downloaded Huginn 0.1.1 runner")
        Path jar;

        @Override
        int execute() throws Exception {
            spec.commandLine().getOut().println(new HuginnProbe().check(project, jar).toPrettyString());
            return 0;
        }
    }

    @Command(name = "validate-vex", description = "Validate OpenVEX against the bundled pinned upstream JSON Schema")
    static class ValidateVex extends Operation {
        @Option(names = "--file", required = true)
        Path file;

        @Override
        int execute() throws Exception {
            SchemaChecks.validate("openvex-v0.2.0.schema.json", EvidenceHasher.read(file));
            spec.commandLine().getOut().println("OpenVEX 0.2.0 schema: PASS");
            return 0;
        }
    }
}
