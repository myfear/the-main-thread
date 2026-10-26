package com.themainthread.vex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

final class OpenVexWriter {
    JsonNode export(Path root) throws IOException {
        Path reviewPath = EvidenceHasher.safe(root, "review.json");
        if (!Files.isRegularFile(reviewPath)) throw new CaseException(5, "Approval missing");
        Path reviewHash = EvidenceHasher.safe(root, "review.sha256");
        if (!Files.isRegularFile(reviewHash) || !Files.readString(reviewHash).strip().equals(EvidenceHasher.hash(reviewPath)))
            throw new CaseException(5, "Approval file changed");
        JsonNode savedReport = EvidenceHasher.read(EvidenceHasher.safe(root, "verify-report.json"));
        var freshReport = new AssessmentVerifier().verify(root);
        if (!freshReport.path("valid").asBoolean()) throw new CaseException(freshReport.path("exit_code").asInt(), freshReport.path("reason").asText());
        if (!savedReport.equals(freshReport)) throw new CaseException(5, "Verification report is stale");
        JsonNode review = EvidenceHasher.read(reviewPath);
        if (!review.path("approved").asBoolean() || !review.path("bindings").equals(ApprovalService.bindings(root))
                || review.path("reviewer").asText().isBlank() || review.path("reason").asText().isBlank())
            throw new CaseException(5, "Approval missing, denied, or stale");
        Instant.parse(review.path("timestamp").asText());
        JsonNode manifest = EvidenceHasher.read(root.resolve("manifest.json"));
        JsonNode proposal = EvidenceHasher.read(root.resolve("assessment-proposal.json"));
        boolean synthetic = manifest.path("test_only").asBoolean();
        var vex = EvidenceHasher.object();
        vex.put("@context", "https://openvex.dev/ns/v0.2.0").put("@id", "urn:uuid:" + UUID.randomUUID())
                .put("author", (synthetic ? "TEST_ONLY / " : "") + review.path("reviewer").asText())
                .put("timestamp", Instant.now().toString()).put("version", 1);
        var statements = vex.putArray("statements");
        HashSet<String> emitted = new HashSet<>();
        for (JsonNode assessment : proposal.path("assessments")) {
            String id = assessment.path("vulnerability_id").asText();
            if (!emitted.add(id)) continue;
            var statement = statements.addObject();
            statement.putObject("vulnerability").put("name", id);
            statement.putArray("products").addObject().put("@id", manifest.path("product_purl").asText());
            statement.put("status", assessment.path("proposed_status").asText());
            for (String field : java.util.List.of("justification", "impact_statement", "action_statement"))
                if (!assessment.path(field).asText("").isBlank()) statement.put(field, assessment.get(field).asText());
        }
        SchemaChecks.validate("openvex-v0.2.0.schema.json", vex);
        EvidenceHasher.write(EvidenceHasher.safe(root, "vex.json"), vex);
        EvidenceHasher.writeText(EvidenceHasher.safe(root, "assessment.md"), "# " + (synthetic ? "TEST_ONLY " : "") + "VEX assessment\n\nProduct: `"
                + manifest.path("product_purl").asText() + "`\n\nReviewer: " + review.path("reviewer").asText()
                + "\n\nReview reason: " + review.path("reason").asText() + "\n\nStatic dependency usage is not CVE reachability.\n\n"
                + "Local reviewer identity is not authenticated. Hashes do not prevent a writer replacing both inputs and manifest.\n\n"
                + "```json\n" + proposal.toPrettyString() + "\n```\n");
        return vex;
    }
}
