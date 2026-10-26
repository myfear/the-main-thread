package com.themainthread.vex;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class ApprovalService {
    void approve(Path root) throws IOException {
        Console console = System.console();
        if (console == null) throw new CaseException(5, "Approval requires an interactive terminal; no unattended flags are supported");
        JsonNode report = verifiedReport(root);
        JsonNode manifest = EvidenceHasher.read(EvidenceHasher.safe(root, "manifest.json"));
        JsonNode proposal = EvidenceHasher.read(EvidenceHasher.safe(root, "assessment-proposal.json"));
        console.printf("Product: %s%nCase: %s%n%s%n%s%n", manifest.path("product_purl"), manifest.path("case_id"), proposal.toPrettyString(), report.toPrettyString());
        console.printf("You are approving a local VEX claim. Your name is recorded, not authenticated.%n");
        if (!manifest.path("git_status").asText().isBlank()) {
            String dirty = console.readLine("Inspect original source and packaged hashes; type REVIEWED DIRTY SOURCE to accept this snapshot: ");
            if (!"REVIEWED DIRTY SOURCE".equals(dirty)) throw new CaseException(5, "Dirty source review not confirmed");
        }
        String confirmation = console.readLine("Type the exact case ID to approve (anything else rejects): ");
        boolean approved = manifest.path("case_id").asText().equals(confirmation);
        String reviewer = console.readLine("Reviewer name: ");
        String reason = console.readLine("Reason, assumptions, and manual security review: ");
        saveReview(root, approved, reviewer, reason);
        if (!approved) throw new CaseException(5, "Review rejected");
    }

    JsonNode verifiedReport(Path root) throws IOException {
        var report = new AssessmentVerifier().verify(root);
        if (!report.path("valid").asBoolean()) throw new CaseException(report.path("exit_code").asInt(), report.path("reason").asText());
        EvidenceHasher.write(EvidenceHasher.safe(root, "verify-report.json"), report);
        return report;
    }

    // Package-private for deterministic tests and the visibly synthetic offline demo. No real-case CLI bypass.
    void saveReview(Path root, boolean approved, String reviewer, String reason) throws IOException {
        if (reviewer == null || reviewer.isBlank() || reason == null || reason.isBlank()) throw new CaseException(5, "Reviewer and reason are required");
        verifiedReport(root);
        var review = EvidenceHasher.object();
        review.put("approved", approved).put("reviewer", reviewer).put("reason", reason).put("timestamp", Instant.now().toString());
        review.set("bindings", bindings(root));
        EvidenceHasher.write(EvidenceHasher.safe(root, "review.json"), review);
        EvidenceHasher.writeText(EvidenceHasher.safe(root, "review.sha256"), EvidenceHasher.hash(root.resolve("review.json")) + "\n");
    }

    static ObjectNode bindings(Path root) throws IOException {
        var hashes = EvidenceHasher.object();
        for (String name : java.util.List.of("manifest.json", "assessment-proposal.json", "verify-report.json"))
            hashes.put(name, EvidenceHasher.hash(EvidenceHasher.safe(root, name)));
        return hashes;
    }
}
