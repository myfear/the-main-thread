package com.themainthread.studio;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;

@QuarkusTest
@QuarkusTestResource(SpikeTestEnvironment.class)
class RuntimeSpikeTest {
    private static final String RUNNER_TOKEN = "test-spike-runner";
    private static final AtomicInteger VERSIONS = new AtomicInteger();

    @Inject
    EventJournal journal;

    @Inject
    AgroalDataSource dataSource;

    @ConfigProperty(name = "quarkus.flow.runner.source.path")
    String workflowDirectory;

    @Test
    void discoversNewImmutableWorkflowRunsBothBranchesAndPersistsEngineAndStudioRecords() throws Exception {
        String version = publish();
        String lowRunId = UUID.randomUUID().toString();
        String low = run(version, lowRunId, 12, false);
        assertPostgresPersistence(low, version);
        awaitCompleted(low);
        var lowEvents = journal.events(low);
        assertEquals("WORKFLOW_STARTED", lowEvents.getFirst().kind());
        assertTrue(lowEvents.stream().allMatch(e -> lowRunId.equals(e.runId()) && version.equals(e.version())
                && "studio".equals(e.namespace()) && "risk-triage".equals(e.name())));
        assertTrue(lowEvents.stream().anyMatch(e -> "TASK_STARTED".equals(e.kind()) && "policy".equals(e.task())));
        assertTrue(lowEvents.stream().anyMatch(e -> "TASK_COMPLETED".equals(e.kind()) && "policy".equals(e.task())));
        assertTrue(lowEvents.stream().anyMatch(e -> "lowRiskOutput".equals(e.task())));
        assertFalse(lowEvents.stream().anyMatch(e -> "humanReview".equals(e.task())));
        assertEquals("LOW_RISK", lowEvents.getLast().output().get("decision").asText());

        String high = run(version, UUID.randomUUID().toString(), 250, false);
        assertPostgresPersistence(high, version);
        awaitCompleted(high);
        var highEvents = journal.events(high);
        assertTrue(highEvents.stream().anyMatch(e -> "humanReview".equals(e.task())));
        assertFalse(highEvents.stream().anyMatch(e -> "lowRiskOutput".equals(e.task())));
        assertEquals("REQUIRES_HUMAN_REVIEW", highEvents.getLast().output().get("decision").asText());
        assertNotEquals(low, high);

        await(() -> checkpointCount(low) == 0 && checkpointCount(high) == 0);
        assertFalse(journal.events(low).isEmpty(), "Studio history survives engine checkpoint cleanup.");
        assertEquals(0, journal.lostEvents());
    }

    @Test
    void executor503FaultsTheActualTaskAndWorkflow() throws Exception {
        String version = publish();
        String instance = run(version, UUID.randomUUID().toString(), 12, true);
        await(() -> events(instance).stream().anyMatch(e -> "WORKFLOW_FAILED".equals(e.kind())));
        var events = journal.events(instance);
        assertTrue(events.stream().anyMatch(e -> "TASK_FAILED".equals(e.kind()) && "policy".equals(e.task())));
        assertFalse(events.stream().anyMatch(e -> "WORKFLOW_COMPLETED".equals(e.kind())));
        assertFalse(events.stream().anyMatch(e -> "lowRiskOutput".equals(e.task()) || "humanReview".equals(e.task())));
        assertEquals(0, journal.lostEvents());
    }

    @Test
    void rejectsMissingCredentialsAndKeepsPublishedVersionsImmutable() throws Exception {
        given().contentType("application/json").body(Map.of("version", "0.0.1"))
                .post("/api/spike/workflows").then().statusCode(401);
        given().contentType("application/json")
                .body(Map.of("runId", UUID.randomUUID().toString(), "changedLines", 1, "fail", false))
                .post("/internal/spike/policy").then().statusCode(401);
        given().contentType("application/json").body(Map.of())
                .post("/q/flow/exec/studio/risk-triage/0.0.1").then().statusCode(401);

        String first = publish();
        byte[] artifact = Files.readAllBytes(Path.of(workflowDirectory, "risk-triage-" + first + ".yaml"));
        given().auth().oauth2(RUNNER_TOKEN).contentType("application/json").body(Map.of("version", first))
                .post("/api/spike/workflows").then().statusCode(409);
        String second = publish();
        assertNotEquals(first, second);
        assertTrue(java.util.Arrays.equals(artifact,
                Files.readAllBytes(Path.of(workflowDirectory, "risk-triage-" + first + ".yaml"))));
        String instance = run(first, UUID.randomUUID().toString(), 1, false);
        awaitCompleted(instance);
        assertTrue(journal.events(instance).stream().allMatch(e -> first.equals(e.version())));
        String yaml = new String(artifact, java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(yaml.contains("test-spike-executor"));
        assertFalse(yaml.contains("test-spike-runner"));
    }

    private String publish() throws Exception {
        String version = "0.0." + VERSIONS.incrementAndGet();
        given().auth().oauth2(RUNNER_TOKEN).get("/api/spike/workflows/" + version).then().statusCode(404);
        given().auth().oauth2(RUNNER_TOKEN).contentType("application/json").body(Map.of("version", version))
                .post("/api/spike/workflows").then().statusCode(202);
        await(() -> given().auth().oauth2(RUNNER_TOKEN).get("/api/spike/workflows/" + version).statusCode() == 200);
        assertTrue(Files.isRegularFile(Path.of(workflowDirectory, "risk-triage-" + version + ".yaml")));
        return version;
    }

    private String run(String version, String runId, int changedLines, boolean fail) {
        Response accepted = given().auth().oauth2(RUNNER_TOKEN).contentType("application/json")
                .body(Map.of("runId", runId, "changedLines", changedLines, "fail", fail))
                .post("/q/flow/exec/studio/risk-triage/" + version);
        assertEquals(202, accepted.statusCode(), accepted.asString());
        String instance = accepted.jsonPath().getString("instanceId");
        assertTrue(instance != null && !instance.isBlank());
        return instance;
    }

    private void awaitCompleted(String instance) throws Exception {
        await(() -> events(instance).stream().anyMatch(e -> "WORKFLOW_COMPLETED".equals(e.kind())));
    }

    private List<EventJournal.StoredEvent> events(String instance) {
        try {
            return journal.events(instance);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertPostgresPersistence(String instance, String version) throws Exception {
        await(() -> checkpointCount(instance) > 0);
        try (var connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            try (var statement = connection.prepareStatement("""
                    SELECT workflow_namespace, workflow_name, workflow_version
                    FROM workflow_instance_entity WHERE instance_id = ?
                    """)) {
                statement.setString(1, instance);
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next(), "The engine instance must be stored separately from Studio events.");
                    assertEquals("studio", rows.getString(1));
                    assertEquals("risk-triage", rows.getString(2));
                    assertEquals(version, rows.getString(3));
                }
            }
        }
        assertTrue(journal.checkpoints(instance) > 0, "The engine must write task checkpoints.");
    }

    private long checkpointCount(String instance) {
        try {
            return journal.checkpoints(instance);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Runtime spike condition did not become true within 20 seconds.");
            }
            Thread.sleep(50);
        }
    }
}
