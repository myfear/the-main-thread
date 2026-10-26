package com.themainthread.vex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

class PipelineTest {
    @TempDir Path temporary;
    Path project;
    Path sbom;
    Path root;
    HttpServer server;
    String base;
    String behavior = "success";
    final AtomicInteger queries = new AtomicInteger();

    @BeforeEach
    void setup() throws Exception {
        project = temporary.resolve("project");
        Files.createDirectories(project.resolve("src/main"));
        Files.createDirectories(project.resolve("target/quarkus-app/lib"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("src/main/Fixture.java"), "// TEST_ONLY no executable vulnerability\n");
        Files.writeString(project.resolve("target/quarkus-app/quarkus-run.jar"), "TEST_ONLY runner bytes");
        Files.writeString(project.resolve("target/quarkus-app/lib/fixture.jar"), "TEST_ONLY library bytes");
        Files.writeString(project.resolve(".gitignore"), "target/\n");
        ProjectSnapshot.git(project, "init", "-q");
        ProjectSnapshot.git(project, "add", ".");
        ProjectSnapshot.git(project, "-c", "user.name=TEST_ONLY", "-c", "user.email=test@example.invalid", "commit", "-qm", "fixture");
        sbom = project.resolve("target/distribution.json");
        root = temporary.resolve("case");
        EvidenceHasher.write(sbom, inventory(true, false));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> {
            boolean detail = exchange.getRequestURI().getPath().startsWith("/v1/vulns/");
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!detail) queries.incrementAndGet();
            int status = switch (behavior) {
                case "503" -> 503;
                case "429" -> 429;
                case "400" -> 400;
                case "enrichment" -> detail ? 503 : 200;
                default -> 200;
            };
            if (behavior.equals("timeout")) {
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            String response = detail ? OfflineDemo.advisory().toString() : switch (behavior) {
                case "empty" -> "{}";
                case "malformed" -> "{\"vulns\":[";
                case "batch-shape" -> "{\"results\":[]}";
                case "unknown-shape" -> "{\"message\":\"TEST_ONLY invalid success shape\"}";
                case "pagination-missing" -> request.contains("page_token") ? "{\"vulns\":[" : "{\"next_page_token\":\"page2\"}";
                case "pagination" -> request.contains("page_token") ? "{}" : "{\"vulns\":[{\"id\":\"" + OfflineDemo.ID + "\"}],\"next_page_token\":\"page2\"}";
                case "repeated-page" -> "{\"next_page_token\":\"same\"}";
                default -> "{\"vulns\":[{\"id\":\"" + OfflineDemo.ID + "\"}]}";
            };
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
            } catch (IOException ignored) { /* A timeout intentionally disconnects the client. */ }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach void stop() { server.stop(0); }

    ObjectNode inventory(boolean includeLibrary, boolean build) throws IOException {
        var document = EvidenceHasher.object();
        document.put("bomFormat", "CycloneDX").put("specVersion", "1.6");
        var product = document.putObject("metadata").putObject("component");
        product.put("purl", "pkg:maven/test/sample@1.0.0").put("type", "application");
        product.putArray("hashes").addObject().put("alg", "SHA-256").put("content", EvidenceHasher.hash(project.resolve("target/quarkus-app/quarkus-run.jar")));
        product.putObject("evidence").putArray("occurrences").addObject().put("location", "quarkus-run.jar");
        var components = document.putArray("components");
        if (includeLibrary) {
            var library = components.addObject();
            library.put("type", "library").put("purl", "pkg:maven/org.apache.commons/commons-text@1.9?type=jar")
                    .put("bom-ref", "module-a-text-1.9").put("version", "1.9");
            if (build) library.put("scope", "excluded");
            library.putArray("properties").addObject().put("name", "quarkus:component:scope").put("value", build ? "development" : "runtime");
            library.putArray("hashes").addObject().put("alg", "SHA-256").put("content", EvidenceHasher.hash(project.resolve("target/quarkus-app/lib/fixture.jar")));
            library.putObject("evidence").putArray("occurrences").addObject().put("location", "lib/fixture.jar");
        }
        document.putArray("dependencies");
        return document;
    }

    void prepare(Path dependency) throws IOException {
        new CasePreparer().prepare(project, sbom, dependency, root, base, null, true, Duration.ofMillis(150));
    }

    ObjectNode propose() throws IOException {
        var proposal = OfflineDemo.proposal(root);
        EvidenceHasher.write(root.resolve("assessment-proposal.json"), proposal);
        return proposal;
    }

    ObjectNode verify() { return new AssessmentVerifier().verify(root); }

    @Test void honestUnknownExportsSchemaValidProductStatement() throws Exception {
        prepare(null);
        propose();
        assertTrue(verify().path("valid").asBoolean(), verify().toPrettyString());
        assertEquals(5, assertThrows(CaseException.class, () -> new OpenVexWriter().export(root)).code);
        new ApprovalService().saveReview(root, true, "TEST_ONLY reviewer", "TEST_ONLY reviewed uncertainty");
        JsonNode vex = new OpenVexWriter().export(root);
        SchemaChecks.validate("openvex-v0.2.0.schema.json", vex);
        assertEquals("pkg:maven/test/sample@1.0.0", vex.at("/statements/0/products/0/@id").asText());
        assertEquals("under_investigation", vex.at("/statements/0/status").asText());
        assertTrue(vex.path("author").asText().startsWith("TEST_ONLY"));
    }

    @ParameterizedTest @ValueSource(strings = { "503", "429", "400", "timeout", "malformed", "batch-shape", "unknown-shape", "pagination-missing", "enrichment", "repeated-page" })
    void feedErrorsNeverBecomeZeroFindings(String failure) throws Exception {
        behavior = failure;
        prepare(null);
        JsonNode feed = EvidenceHasher.read(root.resolve("evidence/vulnerability-findings.json"));
        assertFalse(feed.path("complete").asBoolean());
        assertFalse(feed.at("/components/0/complete").asBoolean());
        assertTrue(feed.at("/components/0/error").isTextual());
        assertRefused(verify(), "inventory_and_feed_coverage", 3, "Vulnerability feed incomplete; export blocked");
        if (failure.equals("503") || failure.equals("429")) assertEquals(3, queries.get());
        if (failure.equals("400") || failure.equals("malformed")) assertEquals(1, queries.get());
    }

    @Test void emptySuccessIsDistinct() throws Exception {
        behavior = "empty";
        prepare(null);
        var feed = EvidenceHasher.read(root.resolve("evidence/vulnerability-findings.json"));
        assertTrue(feed.path("complete").asBoolean());
        assertEquals(0, feed.at("/components/0/findings").size());
        // No clean-product statement is emitted for an empty finding set.
        assertFalse(verify().path("valid").asBoolean());
    }

    @Test void paginationCompletesAllPages() throws Exception {
        behavior = "pagination";
        prepare(null);
        propose();
        assertEquals(2, queries.get());
        assertTrue(verify().path("valid").asBoolean());
    }

    @ParameterizedTest @ValueSource(strings = { "case_id", "product_purl", "git_commit", "sbom_sha256", "schema_version", "extra_field" })
    void rejectsProposalIdentityAndUnknownFields(String field) throws Exception {
        prepare(null);
        var proposal = propose();
        proposal.put(field, "invented");
        EvidenceHasher.write(root.resolve("assessment-proposal.json"), proposal);
        var report = verify();
        assertFalse(report.path("valid").asBoolean());
        if (field.equals("case_id")) {
            assertRefused(report, "proposal_schema_and_identity", 4, "Proposal identity mismatch: case_id");
        }
    }

    @ParameterizedTest @ValueSource(strings = { "../outside", "/tmp/outside", "evidence/made-up.json", "input/link.json" })
    void rejectsOutsideInventedAndSymlinkPaths(String path) throws Exception {
        prepare(null);
        var proposal = propose();
        if (path.endsWith("link.json")) Files.createSymbolicLink(root.resolve(path), sbom);
        ((ObjectNode) proposal.at("/assessments/0/evidence/0")).put("path", path);
        EvidenceHasher.write(root.resolve("assessment-proposal.json"), proposal);
        assertFalse(verify().path("valid").asBoolean());
    }

    @ParameterizedTest @ValueSource(strings = { "input/distribution-sbom.json", "evidence/vulnerability-findings.json", "manifest.json" })
    void rejectsOneByteTampering(String relative) throws Exception {
        prepare(null);
        propose();
        Files.writeString(root.resolve(relative), Files.readString(root.resolve(relative)) + " ");
        String reason = relative.equals("manifest.json") ? "Manifest hash changed" : "Frozen evidence changed: " + relative;
        assertRefused(verify(), "case_files", 4, reason);
    }

    @Test void rejectsMissingEvidenceAndWrongHashOrLocator() throws Exception {
        prepare(null);
        var proposal = propose();
        ((ObjectNode) proposal.at("/assessments/0/evidence/0")).put("sha256", "0".repeat(64));
        EvidenceHasher.write(root.resolve("assessment-proposal.json"), proposal);
        assertFalse(verify().path("valid").asBoolean());
        proposal = propose();
        ((ObjectNode) proposal.at("/assessments/0/evidence/0")).put("locator", "execute arbitrary query");
        EvidenceHasher.write(root.resolve("assessment-proposal.json"), proposal);
        assertFalse(verify().path("valid").asBoolean());
        Files.delete(root.resolve("input/distribution-sbom.json"));
        assertFalse(verify().path("valid").asBoolean());
    }

    @ParameterizedTest @ValueSource(strings = { "vulnerable_code_not_in_execute_path", "component_not_present" })
    void unusedRuntimeLibraryCannotBeCleared(String justification) throws Exception {
        prepare(null);
        var proposal = propose();
        var assessment = (ObjectNode) proposal.at("/assessments/0");
        assessment.put("proposed_status", "not_affected").put("justification", justification).put("reasoning", "Pilot UNUSED, but reflection and CDI remain uncertain");
        EvidenceHasher.write(root.resolve("assessment-proposal.json"), proposal);
        assertRefused(verify(), "claims_and_evidence", 4,
                "not_affected requires independently checked build-only component_not_present; UNUSED is insufficient");
    }

    @Test void buildOnlyAbsenceRequiresBothInventoriesAndReview() throws Exception {
        EvidenceHasher.write(sbom, inventory(false, false));
        Path dependency = project.resolve("target/dependency.json");
        EvidenceHasher.write(dependency, inventory(true, true));
        prepare(dependency);
        var proposal = propose();
        ((ObjectNode) proposal.at("/assessments/0")).put("proposed_status", "not_affected").put("justification", "component_not_present");
        EvidenceHasher.write(root.resolve("assessment-proposal.json"), proposal);
        assertTrue(verify().path("valid").asBoolean(), verify().toPrettyString());
        assertEquals(5, assertThrows(CaseException.class, () -> new OpenVexWriter().export(root)).code);
        new ApprovalService().saveReview(root, true, "TEST_ONLY", "TEST_ONLY deployment scope only; build-time risk not assessed");
        assertEquals("not_affected", new OpenVexWriter().export(root).at("/statements/0/status").asText());
    }

    @Test void versionAndModuleCollisionsRemainDistinct() throws Exception {
        var document = inventory(true, false);
        var other = document.withArray("components").get(0).deepCopy();
        ((ObjectNode) other).put("purl", "pkg:maven/org.apache.commons/commons-text@1.10.0?type=jar").put("version", "1.10.0").put("bom-ref", "module-b-text-1.10");
        document.withArray("components").add(other);
        EvidenceHasher.write(sbom, document);
        var inventory = new SbomReader().read(sbom, null, false);
        assertEquals(2, inventory.components().size());
        assertFalse(inventory.components().get(0).key().equals(inventory.components().get(1).key()));
        ((ObjectNode) other).put("bom-ref", "module-a-text-1.9");
        document.withArray("components").set(1, other);
        EvidenceHasher.write(sbom, document);
        assertThrows(CaseException.class, () -> new SbomReader().read(sbom, null, false));
    }

    @ParameterizedTest @ValueSource(strings = { "fixed", "affected" })
    void unsupportedStatusOrMissingActionFails(String status) throws Exception {
        prepare(null);
        var proposal = propose();
        ((ObjectNode) proposal.at("/assessments/0")).put("proposed_status", status);
        EvidenceHasher.write(root.resolve("assessment-proposal.json"), proposal);
        assertFalse(verify().path("valid").asBoolean());
    }

    @Test void staleDeniedAndEditedReviewsCannotExport() throws Exception {
        prepare(null);
        propose();
        var approvals = new ApprovalService();
        approvals.saveReview(root, false, "TEST_ONLY", "TEST_ONLY reject");
        assertEquals(5, assertThrows(CaseException.class, () -> new OpenVexWriter().export(root)).code);
        approvals.saveReview(root, true, "TEST_ONLY", "TEST_ONLY accept");
        Files.writeString(root.resolve("review.json"), Files.readString(root.resolve("review.json")) + " ");
        assertEquals(5, assertThrows(CaseException.class, () -> new OpenVexWriter().export(root)).code);
        approvals.saveReview(root, true, "TEST_ONLY", "TEST_ONLY accept again");
        var proposal = propose();
        ((ObjectNode) proposal.at("/assessments/0")).put("reasoning", "changed after review");
        EvidenceHasher.write(root.resolve("assessment-proposal.json"), proposal);
        assertEquals(5, assertThrows(CaseException.class, () -> new OpenVexWriter().export(root)).code);
    }

    @Test void targetChangesAndWrongCommitFailWithoutModifyingTarget() throws Exception {
        var before = ProjectSnapshot.capture(project, sbom, null);
        prepare(null);
        propose();
        new ApprovalService().saveReview(root, true, "TEST_ONLY", "TEST_ONLY acceptance");
        new OpenVexWriter().export(root);
        assertEquals(before, ProjectSnapshot.capture(project, sbom, null));
        Files.writeString(project.resolve("src/main/Fixture.java"), "// changed\n");
        assertRefused(verify(), "original_product_and_source", 4, "Original source, SBOM, or Fast JAR changed");
        assertThrows(CaseException.class, () -> new OpenVexWriter().export(root));
    }

    @Test void refusesReuseInvalidProductAndFakeArtifactIdentity() throws Exception {
        prepare(null);
        assertThrows(CaseException.class, () -> prepare(null));
        var invalid = inventory(true, false);
        ((ObjectNode) invalid.at("/metadata/component")).put("purl", "not-a-product");
        EvidenceHasher.write(sbom, invalid);
        assertThrows(CaseException.class, () -> new SbomReader().read(sbom, null, false));
        EvidenceHasher.write(sbom, inventory(true, false));
        Files.writeString(project.resolve("target/quarkus-app/lib/fixture.jar"), "other bytes");
        assertThrows(CaseException.class, () -> ProjectSnapshot.capture(project, sbom, null));
    }

    @Test void advisoryRangesAndVersionIdentityAreChecked() {
        var advisory = OfflineDemo.advisory();
        assertTrue(OsvClient.affects(advisory, SbomReader.coordinate("pkg:maven/org.apache.commons/commons-text@1.9")));
        assertFalse(OsvClient.affects(advisory, SbomReader.coordinate("pkg:maven/org.apache.commons/commons-text@1.10.0")));
        assertFalse(OsvClient.affects(advisory, SbomReader.coordinate("pkg:maven/other/component@1.9")));
    }

    private void assertRefused(JsonNode report, String failedStage, int code, String reason) {
        List<String> stages = List.of("case_files", "original_product_and_source", "inventory_and_feed_coverage",
                "proposal_schema_and_identity", "claims_and_evidence");
        int failedIndex = stages.indexOf(failedStage);
        List<String> reported = new ArrayList<>();
        report.path("machine_checks").fieldNames().forEachRemaining(reported::add);
        assertEquals(stages.subList(0, failedIndex + 1), reported);
        for (int index = 0; index < failedIndex; index++) {
            assertEquals("PASS", report.path("machine_checks").path(stages.get(index)).asText());
        }
        assertEquals("FAIL", report.path("machine_checks").path(failedStage).asText());
        assertFalse(report.path("valid").asBoolean());
        assertTrue(report.path("human_review_required").asBoolean());
        assertEquals(code, report.path("exit_code").asInt());
        assertEquals(reason, report.path("reason").asText());
        assertEquals(reason, report.path("rejected_claims").get(0).asText());
    }

    @ParameterizedTest
    @CsvSource({
            "fixed,1.10,1.9,true",
            "fixed,1.10,1.10,false",
            "last_affected,1.9,1.9,true",
            "last_affected,1.9,1.10,false",
            "limit,1.10,1.9,true",
            "limit,1.10,1.10,false"
    })
    void advisoryRangeEndpointsKeepTheirInclusiveOrExclusiveMeaning(String endpoint, String boundary,
            String version, boolean expected) {
        var advisory = OfflineDemo.advisory();
        var affected = (ObjectNode) advisory.path("affected").get(0);
        affected.remove("versions");
        var range = affected.putArray("ranges").addObject();
        range.put("type", "ECOSYSTEM");
        var events = range.putArray("events");
        events.addObject().put("introduced", "0");
        events.addObject().put(endpoint, boundary);
        var coordinate = SbomReader.coordinate("pkg:maven/org.apache.commons/commons-text@" + version);
        assertEquals(expected, OsvClient.affects(advisory, coordinate));
    }
}
