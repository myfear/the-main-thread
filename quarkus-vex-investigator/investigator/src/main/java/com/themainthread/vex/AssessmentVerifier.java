package com.themainthread.vex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class AssessmentVerifier {
    private record FrozenCase(Path root, JsonNode manifest, String manifestHash) {
        boolean hasDependencySbom() {
            return manifest.has("source_dependency_sbom");
        }
    }

    private record CheckedInventory(SbomReader.Inventory runtime, List<SbomReader.Component> components,
            Map<String, JsonNode> findings) {
    }

    ObjectNode verify(Path root) {
        var report = newReport();
        var checks = (ObjectNode) report.get("machine_checks");
        var warnings = (ArrayNode) report.get("warnings");
        String stage = "case_files";
        try {
            FrozenCase frozen = checkCaseFiles(root);
            checks.put(stage, "PASS");

            stage = "original_product_and_source";
            checkOriginalProductAndSource(frozen, warnings);
            checks.put(stage, "PASS");

            stage = "inventory_and_feed_coverage";
            CheckedInventory inventory = checkInventoryAndFeedCoverage(frozen);
            checks.put(stage, "PASS");

            stage = "proposal_schema_and_identity";
            JsonNode proposal = checkProposalSchemaAndIdentity(frozen);
            checks.put(stage, "PASS");

            stage = "claims_and_evidence";
            checkClaimsAndEvidence(frozen, inventory, proposal);
            checks.put(stage, "PASS");
            recordSuccess(report, frozen);
        } catch (Exception failure) {
            recordFailure(report, checks, stage, failure);
        }
        return report;
    }

    private ObjectNode newReport() {
        var report = EvidenceHasher.object();
        report.putObject("machine_checks");
        report.putArray("warnings");
        report.put("human_review_required", true);
        report.putArray("rejected_claims");
        return report;
    }

    private FrozenCase checkCaseFiles(Path directory) throws IOException {
        Path root = directory.toRealPath();
        JsonNode manifest = EvidenceHasher.read(EvidenceHasher.safe(root, "manifest.json"));
        String manifestHash = EvidenceHasher.hash(root.resolve("manifest.json"));
        String recordedHash = Files.readString(EvidenceHasher.safe(root, "manifest.sha256")).strip();
        EvidenceHasher.require(manifestHash.equals(recordedHash), "Manifest hash changed");
        EvidenceHasher.require(manifest.path("schema_version").asInt() == 1
                && manifest.path("manifest_revision").asInt() == 1
                && manifest.path("packaging").asText().equals("fast-jar"), "Unknown manifest version/packaging");
        EvidenceHasher.require(manifest.path("files").isObject() && manifest.path("files").size() > 0,
                "Empty evidence manifest");
        for (var entry : manifest.path("files").properties()) {
            Path evidence = EvidenceHasher.safe(root, entry.getKey());
            EvidenceHasher.require(Files.isRegularFile(evidence)
                    && EvidenceHasher.hash(evidence).equals(entry.getValue().asText()),
                    "Frozen evidence changed: " + entry.getKey());
        }
        return new FrozenCase(root, manifest, manifestHash);
    }

    private void checkOriginalProductAndSource(FrozenCase frozen, ArrayNode warnings) throws IOException {
        JsonNode manifest = frozen.manifest();
        Path project = Path.of(manifest.path("project").asText());
        Path sbom = Path.of(manifest.path("source_sbom").asText());
        Path dependency = frozen.hasDependencySbom() ? Path.of(manifest.get("source_dependency_sbom").asText()) : null;
        EvidenceHasher.require(manifest.path("git_commit").asText().equals(ProjectSnapshot.git(project, "rev-parse", "HEAD")),
                "Git revision changed");
        EvidenceHasher.require(manifest.path("project_snapshot").equals(ProjectSnapshot.capture(project, sbom, dependency)),
                "Original source, SBOM, or Fast JAR changed");
        EvidenceHasher.require(manifest.path("sbom_sha256").asText()
                .equals(EvidenceHasher.hash(frozen.root().resolve("input/distribution-sbom.json"))), "SBOM identity mismatch");
        addReviewWarnings(manifest, warnings);
    }

    private void addReviewWarnings(JsonNode manifest, ArrayNode warnings) {
        if (!manifest.path("git_status").asText().isBlank()) {
            warnings.add("Uncommitted target source: reviewer must explicitly review this snapshot before approval.");
        }
        warnings.add("static dependency usage is not CVE reachability");
        warnings.add("Local hashes and reviewer names are not signatures or authenticated identities. Run verifier outside Bob.");
        if (manifest.path("test_only").asBoolean()) {
            warnings.add("TEST_ONLY: synthetic/local feed. Never publish as a real VEX assessment.");
        }
    }

    private CheckedInventory checkInventoryAndFeedCoverage(FrozenCase frozen) throws IOException {
        var reader = new SbomReader();
        var runtime = reader.read(frozen.root().resolve("input/distribution-sbom.json"),
                frozen.manifest().path("product_purl").asText(), false);
        List<SbomReader.Component> components = readComponents(frozen, reader, runtime);
        Map<String, SbomReader.Component> inventory = indexComponents(components);
        JsonNode feed = EvidenceHasher.read(frozen.root().resolve("evidence/vulnerability-findings.json"));
        checkFeedCompleteness(frozen.manifest(), feed, components.size());

        Map<String, JsonNode> findings = new HashMap<>();
        Set<String> queried = new HashSet<>();
        for (JsonNode outcome : feed.path("components")) {
            SbomReader.Component component = checkLookupIdentity(outcome, inventory, queried);
            for (JsonNode finding : outcome.path("findings")) {
                checkAdvisory(frozen, component, finding, findings);
            }
        }
        return new CheckedInventory(runtime, components, findings);
    }

    private List<SbomReader.Component> readComponents(FrozenCase frozen, SbomReader reader,
            SbomReader.Inventory runtime) throws IOException {
        List<SbomReader.Component> components = new ArrayList<>(runtime.components());
        if (frozen.hasDependencySbom()) {
            var build = reader.read(frozen.root().resolve("input/dependency-sbom.json"), null, true);
            EvidenceHasher.require(build.sourceModule().equals(runtime.sourceModule()), "Dependency source module mismatch");
            components.addAll(build.components());
        }
        return components;
    }

    private Map<String, SbomReader.Component> indexComponents(List<SbomReader.Component> components) {
        Map<String, SbomReader.Component> inventory = new HashMap<>();
        for (var component : components) {
            EvidenceHasher.require(inventory.put(component.key(), component) == null, "Duplicate inventory identity");
        }
        return inventory;
    }

    private void checkFeedCompleteness(JsonNode manifest, JsonNode feed, int componentCount) {
        if (!manifest.path("osv_complete").asBoolean() || !feed.path("complete").asBoolean()) {
            throw new CaseException(3, "Vulnerability feed incomplete; export blocked");
        }
        EvidenceHasher.require(feed.path("components").isArray() && feed.get("components").size() == componentCount,
                "OSV coverage mismatch");
    }

    private SbomReader.Component checkLookupIdentity(JsonNode outcome, Map<String, SbomReader.Component> inventory,
            Set<String> queried) {
        var component = inventory.get(outcome.path("component_key").asText());
        EvidenceHasher.require(component != null && queried.add(component.key()), "Unknown or duplicate query identity");
        EvidenceHasher.require(outcome.path("component_purl").asText().equals(component.purl())
                && outcome.path("component_ref").asText().equals(component.bomRef())
                && outcome.path("source_module").asText().equals(component.sourceModule())
                && outcome.path("scope").asText().equals(component.scope()), "Lookup identity mismatch");
        if (!outcome.path("complete").asBoolean()) {
            throw new CaseException(3, "Component query incomplete");
        }
        return component;
    }

    private void checkAdvisory(FrozenCase frozen, SbomReader.Component component, JsonNode finding,
            Map<String, JsonNode> findings) throws IOException {
        String id = finding.path("id").asText();
        Path advisoryFile = EvidenceHasher.safe(frozen.root(), finding.path("advisory_path").asText());
        EvidenceHasher.require(EvidenceHasher.hash(advisoryFile).equals(finding.path("advisory_sha256").asText()),
                "Advisory hash mismatch");
        JsonNode advisory = EvidenceHasher.read(advisoryFile);
        EvidenceHasher.require(advisory.path("id").asText().equals(id) && OsvClient.affects(advisory, component.gav()),
                "Advisory/version identity mismatch");
        EvidenceHasher.require(findings.put(key(component, id), finding) == null, "Duplicate finding");
        if (frozen.manifest().path("test_only").asBoolean()) {
            EvidenceHasher.require(id.startsWith("TEST_ONLY-"), "Test cases require TEST_ONLY advisory IDs");
        } else {
            EvidenceHasher.require(!id.startsWith("TEST_ONLY-"), "Synthetic advisory in live case");
        }
    }

    private JsonNode checkProposalSchemaAndIdentity(FrozenCase frozen) throws IOException {
        JsonNode proposal = EvidenceHasher.read(EvidenceHasher.safe(frozen.root(), "assessment-proposal.json"));
        SchemaChecks.validate("assessment-proposal.schema.json", proposal);
        for (String field : List.of("case_id", "product_purl", "git_commit", "sbom_sha256", "test_only")) {
            EvidenceHasher.require(proposal.get(field).equals(frozen.manifest().get(field)),
                    "Proposal identity mismatch: " + field);
        }
        return proposal;
    }

    private void checkClaimsAndEvidence(FrozenCase frozen, CheckedInventory inventory, JsonNode proposal) throws IOException {
        Set<String> assessed = new HashSet<>();
        Map<String, String> productStatuses = new HashMap<>();
        for (JsonNode assessment : proposal.path("assessments")) {
            var component = resolveAssessmentComponent(inventory.components(), assessment);
            String id = assessment.path("vulnerability_id").asText();
            String identity = key(component, id);
            JsonNode finding = inventory.findings().get(identity);
            EvidenceHasher.require(finding != null && assessed.add(identity), "Unknown advisory or duplicate assessment");
            checkConsistentProductStatus(assessment, id, productStatuses);
            checkStatusPreconditions(frozen, inventory, component, assessment, id);
            checkClaimEvidence(frozen, component, finding, assessment);
        }
        EvidenceHasher.require(assessed.size() == inventory.findings().size(), "Proposal omitted findings");
    }

    private SbomReader.Component resolveAssessmentComponent(List<SbomReader.Component> components, JsonNode assessment) {
        List<SbomReader.Component> matches = new ArrayList<>();
        for (var component : components) {
            if (component.purl().equals(assessment.path("component_purl").asText())
                    && component.bomRef().equals(assessment.path("component_ref").asText())
                    && component.sourceModule().equals(assessment.path("source_module").asText())) {
                matches.add(component);
            }
        }
        EvidenceHasher.require(matches.size() == 1, "Ambiguous or unknown assessment component");
        return matches.getFirst();
    }

    private void checkConsistentProductStatus(JsonNode assessment, String id, Map<String, String> productStatuses) {
        String status = assessment.path("proposed_status").asText();
        String previous = productStatuses.putIfAbsent(id, status);
        EvidenceHasher.require(previous == null || previous.equals(status), "Conflicting product statuses for same advisory");
    }

    private void checkStatusPreconditions(FrozenCase frozen, CheckedInventory inventory, SbomReader.Component component,
            JsonNode assessment, String id) {
        String status = assessment.path("proposed_status").asText();
        String justification = assessment.path("justification").asText("");
        if (status.equals("fixed")) {
            throw new CaseException(4, "fixed is unsupported in v1");
        }
        if (status.equals("not_affected")) {
            checkBuildOnlyAbsence(frozen, inventory, component, justification, id);
        } else {
            EvidenceHasher.require(justification.isBlank(), "Justification only allowed for not_affected");
        }
        checkStatusStatements(assessment, status);
    }

    private void checkBuildOnlyAbsence(FrozenCase frozen, CheckedInventory inventory, SbomReader.Component component,
            String justification, String id) {
        EvidenceHasher.require(justification.equals("component_not_present") && component.scope().equals("build")
                && frozen.hasDependencySbom(),
                "not_affected requires independently checked build-only component_not_present; UNUSED is insufficient");
        for (var runtimeComponent : inventory.runtime().components()) {
            EvidenceHasher.require(!runtimeComponent.gav().equals(component.gav()), "Affected component is shipped");
        }
        for (var runtimeComponent : inventory.runtime().components()) {
            EvidenceHasher.require(!inventory.findings().containsKey(key(runtimeComponent, id)),
                    "Same advisory affects a shipped component");
        }
    }

    private void checkStatusStatements(JsonNode assessment, String status) {
        String action = assessment.path("action_statement").asText("");
        if (status.equals("affected")) {
            EvidenceHasher.require(!action.isBlank(), "affected requires an action statement and human decision");
        } else {
            EvidenceHasher.require(action.isBlank(), "Action statement only allowed for affected");
        }
        if (!status.equals("not_affected")) {
            EvidenceHasher.require(assessment.path("impact_statement").asText("").isBlank(),
                    "Impact statement only allowed for not_affected");
        }
    }

    private void checkClaimEvidence(FrozenCase frozen, SbomReader.Component component, JsonNode finding,
            JsonNode assessment) throws IOException {
        boolean inventoryEvidence = false;
        boolean advisoryEvidence = false;
        for (JsonNode evidence : assessment.path("evidence")) {
            String relative = evidence.path("path").asText();
            checkEvidenceFile(frozen, evidence, relative);
            String type = evidence.path("type").asText();
            String locator = evidence.path("locator").asText();
            if (type.equals("sbom")) {
                checkInventoryLocator(component, relative, locator);
                inventoryEvidence = true;
            } else {
                EvidenceHasher.require(locator.equals("document"), "Only document or exact bom-ref locators are supported");
                checkDocumentEvidence(type, relative, finding);
                if (type.equals("advisory")) {
                    advisoryEvidence = true;
                }
            }
        }
        EvidenceHasher.require(inventoryEvidence && advisoryEvidence, "Every claim requires exact inventory and advisory evidence");
    }

    private void checkEvidenceFile(FrozenCase frozen, JsonNode evidence, String relative) throws IOException {
        Path path = EvidenceHasher.safe(frozen.root(), relative);
        EvidenceHasher.require(frozen.manifest().path("files").has(relative)
                && EvidenceHasher.hash(path).equals(evidence.path("sha256").asText()), "Invented or changed proposal evidence");
    }

    private void checkInventoryLocator(SbomReader.Component component, String relative, String locator) {
        String expected = component.scope().equals("build") ? "input/dependency-sbom.json" : "input/distribution-sbom.json";
        EvidenceHasher.require(relative.equals(expected) && locator.equals("bom-ref:" + component.bomRef()),
                "SBOM locator does not identify the component");
    }

    private void checkDocumentEvidence(String type, String relative, JsonNode finding) {
        if (type.equals("advisory")) {
            EvidenceHasher.require(relative.equals(finding.path("advisory_path").asText()), "Wrong advisory evidence");
        } else if (type.equals("findings")) {
            EvidenceHasher.require(relative.equals("evidence/vulnerability-findings.json"), "Wrong findings evidence");
        } else {
            EvidenceHasher.require(relative.equals("evidence/dependency-usage.txt") || relative.equals("evidence/pilot-audit.txt"),
                    "Wrong usage evidence");
        }
    }

    private void recordSuccess(ObjectNode report, FrozenCase frozen) throws IOException {
        var hashes = report.putObject("hashes");
        hashes.put("manifest", frozen.manifestHash())
                .put("proposal", EvidenceHasher.hash(frozen.root().resolve("assessment-proposal.json")));
        report.put("valid", true).put("exit_code", 0).put("reason", "VERIFIED_FACTS_REQUIRE_HUMAN_REVIEW");
    }

    private void recordFailure(ObjectNode report, ObjectNode checks, String stage, Exception failure) {
        checks.put(stage, "FAIL");
        int code = failure instanceof CaseException caseFailure ? caseFailure.code : 4;
        String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        report.put("valid", false).put("exit_code", code).put("reason", reason);
        report.withArray("rejected_claims").add(reason);
    }

    private static String key(SbomReader.Component component, String id) {
        return component.key() + "|" + id;
    }
}
