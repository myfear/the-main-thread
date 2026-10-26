package com.themainthread.vex;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

/** Deliberately synthetic fixture workflow, also exercised against the packaged CLI. */
final class OfflineDemo {
    static final String ID = "TEST_ONLY-COMMONS-TEXT";

    static void run(Path project, Path output) throws IOException {
        run(project, output, "none");
    }

    static void run(Path project, Path output, String failure) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String body = exchange.getRequestURI().getPath().startsWith("/v1/vulns/") ? advisory().toString()
                    : request.contains("org.apache.commons:commons-text") ? "{\"vulns\":[{\"id\":\"" + ID + "\"}]}" : "{}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(failure.equals("outage") ? 503 : 200, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        try {
            var manifest = new CasePreparer().prepare(project, project.resolve("target/quarkus-run-cyclonedx.json"), null, output,
                    "http://127.0.0.1:" + server.getAddress().getPort(), null, true, Duration.ofSeconds(2));
            if (!manifest.path("osv_complete").asBoolean()) throw new CaseException(3, "TEST_ONLY simulated OSV 503: feed incomplete; export blocked");
            var proposal = proposal(output);
            if (failure.equals("unused")) ((ObjectNode) proposal.at("/assessments/0")).put("proposed_status", "not_affected")
                    .put("justification", "vulnerable_code_not_in_execute_path").put("reasoning", "TEST_ONLY Pilot UNUSED does not prove CVE non-reachability");
            EvidenceHasher.write(output.resolve("assessment-proposal.json"), proposal);
            if (failure.equals("tamper")) {
                Path evidence = output.resolve("evidence/dependency-usage.txt");
                java.nio.file.Files.writeString(evidence, java.nio.file.Files.readString(evidence) + " ");
            }
            var report = new AssessmentVerifier().verify(output);
            EvidenceHasher.write(output.resolve("verify-report.json"), report);
            if (!report.path("valid").asBoolean()) throw new CaseException(report.path("exit_code").asInt(), report.path("reason").asText());
            new ApprovalService().saveReview(output, true, "TEST_ONLY simulated reviewer", "TEST_ONLY deterministic fixture; no real human approval");
            new OpenVexWriter().export(output);
        } finally { server.stop(0); }
    }

    static ObjectNode advisory() {
        var advisory = EvidenceHasher.object();
        advisory.put("id", ID).put("modified", "2026-10-25T00:00:00Z").put("summary", "TEST_ONLY synthetic advisory; never publish as real");
        var affected = advisory.putArray("affected").addObject();
        affected.putObject("package").put("ecosystem", "Maven").put("name", "org.apache.commons:commons-text");
        affected.putArray("versions").add("1.9");
        advisory.putArray("aliases");
        return advisory;
    }

    static ObjectNode proposal(Path root) throws IOException {
        JsonNode manifest = EvidenceHasher.read(root.resolve("manifest.json"));
        JsonNode feed = EvidenceHasher.read(root.resolve("evidence/vulnerability-findings.json"));
        var proposal = EvidenceHasher.object();
        proposal.put("schema_version", 1);
        for (String field : java.util.List.of("case_id", "product_purl", "git_commit", "sbom_sha256", "test_only")) proposal.set(field, manifest.get(field));
        var assessments = proposal.putArray("assessments");
        for (JsonNode outcome : feed.path("components")) {
            for (JsonNode finding : outcome.path("findings")) {
                var assessment = assessments.addObject();
                assessment.put("vulnerability_id", finding.path("id").asText());
                for (String field : java.util.List.of("component_purl", "component_ref", "source_module")) assessment.set(field, outcome.get(field));
                assessment.put("proposed_status", "under_investigation").putNull("justification").putNull("impact_statement").putNull("action_statement");
                assessment.put("reasoning", "TEST_ONLY fixture: package presence does not establish exploitability or non-reachability.");
                assessment.putArray("unknowns").add("Runtime reachability and attacker-controlled input");
                assessment.put("recommended_next_step", "Inspect call sites, configuration and runtime wiring with a human reviewer.");
                var evidence = assessment.putArray("evidence");
                String inventory = outcome.path("scope").asText().equals("build") ? "input/dependency-sbom.json" : "input/distribution-sbom.json";
                evidence.addObject().put("type", "sbom").put("path", inventory).put("sha256", manifest.path("files").path(inventory).asText())
                        .put("locator", "bom-ref:" + outcome.path("component_ref").asText()).put("claim", "TEST_ONLY exact inventory entry");
                evidence.addObject().put("type", "advisory").put("path", finding.path("advisory_path").asText()).put("sha256", finding.path("advisory_sha256").asText())
                        .put("locator", "document").put("claim", "TEST_ONLY advisory matches this package version");
            }
        }
        return proposal;
    }
}
