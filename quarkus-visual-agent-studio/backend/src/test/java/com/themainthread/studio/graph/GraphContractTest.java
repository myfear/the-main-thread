package com.themainthread.studio.graph;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.themainthread.studio.EventJournal;
import com.themainthread.studio.SpikeTestEnvironment;
import com.themainthread.studio.StudioConfig;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.WorkflowWriter;
import io.serverlessworkflow.impl.WorkflowApplication;

@QuarkusTest
@QuarkusTestResource(SpikeTestEnvironment.class)
class GraphContractTest {
    private static final String TOKEN = "test-spike-runner";

    @Inject
    ObjectMapper mapper;

    @Inject
    GraphValidator validator;

    @Inject
    GraphCompiler compiler;

    @Inject
    GraphStore store;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    NodeCatalog nodeCatalog;

    @Inject
    StudioConfig studioConfig;

    @Inject
    EventJournal journal;

    @Inject
    WorkflowApplication application;

    @Test
    void catalogAndFixtureDescribeAllThreeEdgeKinds() throws Exception {
        var graph = fixture();
        assertTrue(validator.validate(graph).valid(), validator.validate(graph).toString());
        assertEquals(3, graph.edges().stream().map(StudioGraph.Edge::kind).distinct().count());
        given().get("/api/node-types").then().statusCode(401);
        var catalog = given().auth().oauth2(TOKEN).get("/api/node-types").then().statusCode(200)
                .extract().jsonPath().getList("type", String.class);
        assertTrue(catalog.containsAll(List.of("workflow.input", "ai.agent", "ai.model-ref", "logic.switch")));
        assertFalse(catalog.contains("human.approval"));
        JsonNode descriptors = mapper.readTree(given().auth().oauth2(TOKEN).get("/api/node-types").asString());
        for (JsonNode descriptor : descriptors) {
            if ("ai.agent".equals(descriptor.get("type").asText())) {
                assertTrue(descriptor.at("/capabilityInputs/model/required").asBoolean());
                assertFalse(descriptor.at("/capabilityInputs/tools/required").asBoolean());
            }
        }
        given().auth().oauth2(TOKEN).contentType("application/json").body(graph)
                .post("/api/graphs/validate").then().statusCode(200);
    }

    @Test
    void validatesStructuralFailuresTypesCapabilitiesAndBranchData() throws Exception {
        invalid("DUPLICATE_NODE_ID", tree -> nodes(tree).add(nodes(tree).get(0).deepCopy()));
        invalid("MISSING_ENDPOINT", tree -> ((ObjectNode) edges(tree).get(0)).put("target", "missing"));
        invalid("INVALID_PORT_CONNECTION", tree -> ((ObjectNode) edges(tree).get(0)).remove("sourcePort"));
        invalid("INVALID_PORT_CONNECTION", tree -> ((ObjectNode) edges(tree).get(17)).remove("targetPort"));
        invalid("INVALID_PORT_CONNECTION", tree -> ((ObjectNode) edges(tree).get(6)).put("targetPort", "pullRequest"));
        invalid("DATA_CARDINALITY", tree -> edges(tree).remove(10));
        invalid("CONTROL_CYCLE", tree -> ((ObjectNode) edges(tree).get(1)).put("target", "fetch"));
        invalid("CONTROL_CARDINALITY", tree -> ((ObjectNode) edges(tree).get(5)).put("sourcePort", "false"));
        invalid("CONTROL_CARDINALITY", tree -> ((ObjectNode) edges(tree).get(5)).put("target", "lowOutput"));
        invalid("UNSUPPORTED_NODE", tree -> ((ObjectNode) nodes(tree).get(5)).put("type", "human.approval"));
        invalid("UNSUPPORTED_NODE", tree -> ((ObjectNode) nodes(tree).get(2)).put("version", 2));
        invalid("INVALID_CONFIG", tree -> ((ObjectNode) nodes(tree).get(1).get("config")).put("endpoint", "https://example.org"));
        invalid("INVALID_CONFIG", tree -> ((ObjectNode) nodes(tree).get(2).get("config")).put("maxToolCalls", 999));
        invalid("INVALID_CONFIG", tree -> ((ObjectNode) nodes(tree).get(7).get("config")).put("modelRef", "unknown-model"));
        invalid("INVALID_CONFIG", tree -> ((ArrayNode) nodes(tree).get(9).get("config").get("allowedToolIds")).add("run.shell"));
        invalid("INVALID_PORT_CONNECTION", tree -> ((ObjectNode) edges(tree).get(17)).put("targetPort", "model"));
        invalid("CAPABILITY_CARDINALITY", tree -> edges(tree).remove(15));
        invalid("UNUSED_CAPABILITY", tree -> edges(tree).remove(17));
        invalid("UNAVAILABLE_DATA", tree -> {
            ((ObjectNode) edges(tree).get(0)).put("target", "review");
            ((ObjectNode) edges(tree).get(1)).put("source", "review").put("target", "fetch");
            ((ObjectNode) edges(tree).get(2)).put("source", "fetch");
        });
        invalid("GRAPH_LIMIT", tree -> {
            while (nodes(tree).size() <= GraphValidator.MAX_NODES) {
                nodes(tree).add(nodes(tree).get(0).deepCopy());
            }
        });
    }

    @Test
    void accumulatesDiagnosticsAndRejectsUnknownJsonProperties() throws Exception {
        var tree = (ObjectNode) mapper.valueToTree(fixture());
        ((ObjectNode) nodes(tree).get(2).get("config")).put("endpoint", "http://attacker.invalid");
        ((ObjectNode) edges(tree).get(0)).put("target", "missing");
        var result = validator.validate(mapper.treeToValue(tree, StudioGraph.class));
        assertTrue(result.errors().stream().anyMatch(e -> "review".equals(e.nodeId())));
        assertTrue(result.errors().stream().anyMatch(e -> "e1".equals(e.edgeId())));
        tree.put("arbitraryExpression", "untrusted");
        given().auth().oauth2(TOKEN).contentType("application/json").body(tree.toString())
                .post("/api/graphs/validate").then().statusCode(400);
    }

    @Test
    void compilerIsDeterministicAndCapabilitiesCreateNoTasks() throws Exception {
        var graph = fixture();
        var first = compiler.compile(graph, "0.0.1");
        var reversedNodes = new java.util.ArrayList<>(graph.nodes());
        var reversedEdges = new java.util.ArrayList<>(graph.edges());
        Collections.reverse(reversedNodes);
        Collections.reverse(reversedEdges);
        var reordered = new StudioGraph(1, graph.flowId(), graph.name(), graph.description(),
                reversedNodes, reversedEdges, graph.metadata());
        var second = compiler.compile(reordered, "0.0.1");
        assertEquals(first.yaml(), second.yaml());
        assertEquals(first.graphSha256(), second.graphSha256());
        assertEquals(first.artifactSha256(), second.artifactSha256());
        assertEquals(first.manifest(), second.manifest());
        assertEquals(7, first.manifest().size());
        assertFalse(first.manifest().containsKey("n_model"));
        assertFalse(first.manifest().containsKey("n_prompt"));
        assertFalse(first.manifest().containsKey("n_tools"));
        var agent = first.manifest().get("n_review").agent();
        assertEquals("fixture-model-v1", agent.modelRef());
        assertEquals(GraphCompiler.sha256(agent.promptText()), agent.promptSha256());
        assertEquals(List.of("pr.read-files"), agent.allowedToolIds());
        assertTrue(first.yaml().contains("/internal/graph/execute"));
        assertFalse(first.yaml().contains("test-spike-executor"));
        assertFalse(first.yaml().contains("test-spike-runner"));
    }

    @Test
    void fixtureGraphPublishesRunsBothBranchesAndMapsActualEventsToNodes() throws Exception {
        var graph = fixture();
        UUID publication = publish(graph, "0.0.1");
        JsonNode low = run(publication, "low-risk");
        assertEquals("LOW_RISK", low.at("/output/decision").asText(), low.toPrettyString());
        assertEquals("fixture", low.at("/output/modelMode").asText());
        assertFalse(low.at("/output/requiresApproval").asBoolean());
        assertEquals("LOW", low.at("/output/reviewResult/findings/0/severity").asText());
        SetView lowNodes = executed(low);
        assertTrue(lowNodes.nodes().containsAll(List.of("input", "fetch", "review", "policy", "route", "lowOutput")));
        assertFalse(lowNodes.nodes().contains("highOutput"));
        assertFalse(lowNodes.nodes().contains("model"));
        JsonNode high = run(publication, "high-risk");
        assertEquals("REQUIRES_HUMAN_REVIEW", high.at("/output/decision").asText(), high.toPrettyString());
        assertTrue(high.at("/output/requiresApproval").asBoolean());
        assertTrue(executed(high).nodes().contains("highOutput"));
        assertFalse(executed(high).nodes().contains("lowOutput"));
        assertEquals(0, low.path("lostEvents").asInt());
        assertEquals(0, high.path("lostEvents").asInt());
        var retained = store.get(publication).compiled();
        assertEquals(graph.flowId(), retained.flowId());
        assertEquals("HIGH", retained.manifest().get("n_policy").config().get("minimumSeverity").asText());
    }

    @Test
    void uncertainStartRetainsCompletedHistoryWithoutAnInstanceMapping() throws Exception {
        UUID publication = publish(fixture(), "0.0.1");
        JsonNode completed = run(publication, "high-risk");
        UUID runId = UUID.fromString(completed.get("runId").asText());
        store.started(runId, null, "START_UNCERTAIN");

        JsonNode recovered = snapshot(runId);
        assertTrue(recovered.get("instanceId").isNull());
        assertEquals("COMPLETED", recovered.get("status").asText());
        assertEquals(completed.get("events"), recovered.get("events"));
        assertEquals(completed.get("output"), recovered.get("output"));
        assertTrue(executed(recovered).nodes().contains("highOutput"));

        UUID otherRun = store.createRun(publication);
        store.started(otherRun, null, "START_UNCERTAIN");
        assertTrue(snapshot(otherRun).get("events").isEmpty(), "History is scoped to the reserved run ID.");
        assertEquals("START_UNCERTAIN", store.run(runId).startStatus(), "Reading history must not restart or reconcile a run.");
    }

    @Test
    void uncertainStartShowsPartialEventsWithoutClaimingCompletion() throws Exception {
        UUID publication = publish(fixture(), "0.0.1");
        var compiled = store.get(publication).compiled();
        UUID runId = store.createRun(publication);
        store.started(runId, null, "START_UNCERTAIN");
        journal.append(new EventJournal.PendingEvent("partial-start-test", runId, "studio", compiled.workflowName(),
                compiled.version(), "WORKFLOW_STARTED", null, 0, OffsetDateTime.now(), null));
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        JsonNode partial;
        do {
            partial = snapshot(runId);
            if (!partial.get("events").isEmpty()) {
                break;
            }
            assertTrue(System.nanoTime() < deadline, "The partial lifecycle event was not journaled.");
            Thread.sleep(25);
        } while (true);
        assertEquals("START_UNCERTAIN", partial.get("status").asText());
        assertEquals("WORKFLOW_STARTED", partial.at("/events/0/kind").asText());
        assertTrue(partial.get("output").isNull());
    }

    @Test
    void generatedSwitchAndBothOutputsRequireExplicitFalseForLowRisk() throws Exception {
        var compiled = compiler.compile(fixture(), "0.0.1");
        var workflow = WorkflowReader.validation().read(compiled.yaml(), WorkflowFormat.YAML);
        var source = (ObjectNode) mapper.readTree(WorkflowWriter.workflowAsString(workflow, WorkflowFormat.JSON));
        for (String policyJson : List.of("{}", "{\"requiresApproval\":null}", "{\"requiresApproval\":true}",
                "{\"requiresApproval\":\"false\"}", "{\"requiresApproval\":0}", "{\"requiresApproval\":[]}",
                "{\"requiresApproval\":{}}", "{\"requiresApproval\":false}")) {
            JsonNode policy = mapper.readTree(policyJson);
            boolean reviewRequired = !policy.path("requiresApproval").isBoolean() || policy.get("requiresApproval").asBoolean();
            // Run the actual generated switch and output expressions with injected policy output.
            // Also enter each output directly so neither one can hide a permissive fallback.
            for (String entry : List.of("n_route", "n_lowOutput", "n_highOutput")) {
                ObjectNode probe = source.deepCopy();
                probe.remove("input");
                ((ObjectNode) probe.get("document")).put("namespace", "studio-regression")
                        .put("name", "branch-" + UUID.randomUUID());
                ArrayNode tasks = mapper.createArrayNode();
                tasks.add(mapper.valueToTree(Map.of("seed", Map.of("set", Map.of("nodes", "${ $workflow.input.nodes }"),
                        "export", Map.of("as", "${ $output }"), "then", entry))));
                for (JsonNode task : source.withArray("do")) {
                    if (task.has(entry) || ("n_route".equals(entry) && (task.has("n_lowOutput") || task.has("n_highOutput")))) {
                        ObjectNode copy = task.deepCopy();
                        String taskName = copy.fieldNames().next();
                        if (!"n_route".equals(taskName)) {
                            ((ObjectNode) copy.at("/" + taskName + "/set")).put("selectedOutput", taskName);
                        }
                        tasks.add(copy);
                    }
                }
                probe.set("do", tasks);
                var input = Map.of("runId", UUID.randomUUID().toString(), "nodes", Map.of(
                        "policy", Map.of("outputs", policy), "review", Map.of("outputs", Map.of(
                                "reviewResult", Map.of("summary", "Injected policy regression", "findings", List.of())))));
                var definition = application.workflowDefinition(WorkflowReader.validation().read(probe.toString(), WorkflowFormat.JSON));
                JsonNode result = mapper.valueToTree(definition.instance(input).start().get(10, TimeUnit.SECONDS).asJavaObject());
                assertEquals(reviewRequired, result.path("requiresApproval").asBoolean(), entry + " " + policyJson);
                assertEquals(reviewRequired ? "REQUIRES_HUMAN_REVIEW" : "LOW_RISK", result.path("decision").asText());
                assertEquals("n_route".equals(entry) ? (reviewRequired ? "n_highOutput" : "n_lowOutput") : entry,
                        result.path("selectedOutput").asText());
            }
        }
    }

    @Test
    void rejectsInvalidGraphsBeforeRegistrationAndRetainsEarlierPublishedBehavior() throws Exception {
        var graph = fixture();
        var invalid = (ObjectNode) mapper.valueToTree(graph);
        ((ObjectNode) nodes(invalid).get(1).get("config")).put("url", "http://attacker.invalid");
        long before;
        try (var connection = dataSource.getConnection(); var query = connection.createStatement();
                var rows = query.executeQuery("SELECT count(*) FROM studio.graph_publication")) {
            rows.next();
            before = rows.getLong(1);
        }
        given().auth().oauth2(TOKEN).contentType("application/json")
                .body(Map.of("version", "0.0.1", "graph", invalid)).post("/api/graphs/publish").then().statusCode(422);
        try (var connection = dataSource.getConnection(); var query = connection.createStatement();
                var rows = query.executeQuery("SELECT count(*) FROM studio.graph_publication")) {
            rows.next();
            assertEquals(before, rows.getLong(1));
        }
        UUID first = publish(graph, "0.0.1");
        String original = store.get(first).compiled().yaml();
        var changed = (ObjectNode) mapper.valueToTree(graph);
        ((ObjectNode) nodes(changed).get(3).get("config")).put("minimumSeverity", "LOW");
        UUID second = publish(mapper.treeToValue(changed, StudioGraph.class), "0.0.2");
        assertNotEquals(first, second);
        assertEquals(original, store.get(first).compiled().yaml());
        assertEquals("LOW_RISK", run(first, "low-risk").at("/output/decision").asText());
        assertEquals("REQUIRES_HUMAN_REVIEW", run(second, "low-risk").at("/output/decision").asText());
        given().auth().oauth2(TOKEN).contentType("application/json")
                .body(Map.of("version", "0.0.1", "graph", graph)).post("/api/graphs/publish").then().statusCode(409);
    }

    @Test
    void executorRejectsUnpublishedTaskIdentitiesAndMissingCredentials() throws Exception {
        var graph = fixture();
        UUID publication = publish(graph, "0.0.1");
        JsonNode run = run(publication, "low-risk");
        var body = Map.of("publicationId", publication, "runId", run.get("runId").asText(),
                "taskId", "n_model", "nodeId", "model", "inputs", Map.of());
        given().contentType("application/json").body(body).post("/internal/graph/execute").then().statusCode(401);
        given().header("X-Studio-Internal-Token", "test-spike-executor").contentType("application/json")
                .body(body).post("/internal/graph/execute").then().statusCode(403);
    }

    @Test
    void sensitivePathRequiresReviewEvenWhenModelReturnsNoFindings() throws Exception {
        UUID publication = publish(fixture(), "0.0.1");
        UUID run = store.createRun(publication);
        var review = new ReviewModel.ReviewResult("No model findings", List.of());
        for (var path : Map.of("src/main/java/example/security/Policy.java", true, "security/Policy.java", true,
                "src/security-advice/README.md", false, "docs/security.md", false).entrySet()) {
            var pr = new ReviewModel.PullRequest(55, "Path guard fixture",
                    List.of(new ReviewModel.ChangedFile(path.getKey(), "+ change")));
            var body = Map.of("publicationId", publication, "runId", run, "taskId", "n_policy", "nodeId", "policy",
                    "inputs", Map.of("reviewResult", review, "pullRequest", pr));
            boolean result = given().header("X-Studio-Internal-Token", "test-spike-executor")
                    .contentType("application/json").body(body).post("/internal/graph/execute")
                    .then().statusCode(200).extract().jsonPath().getBoolean("requiresApproval");
            assertEquals(path.getValue(), result, path.getKey());
        }
    }

    @Test
    void modelCannotExceedPublishedToolBudgetOrUseAnUnattachedToolset() throws Exception {
        var tree = (ObjectNode) mapper.valueToTree(fixture());
        ((ObjectNode) nodes(tree).get(2).get("config")).put("maxToolCalls", 1);
        UUID publication = publish(mapper.treeToValue(tree, StudioGraph.class), "0.0.1");
        var request = modelRequest(publication);
        ReviewModel repeatsTool = (pr, configuration, files) -> {
            files.read();
            files.read();
            return new ReviewModel.ReviewResult("Repeated tool call", List.of());
        };
        var repeated = new GraphExecutorResource(studioConfig, store, nodeCatalog, mapper, repeatsTool);
        assertEquals(403, assertThrows(WebApplicationException.class,
                () -> repeated.execute(request, "test-spike-executor")).getResponse().getStatus());

        edges(tree).remove(17);
        nodes(tree).remove(9);
        UUID noTools = publish(mapper.treeToValue(tree, StudioGraph.class), "0.0.2");
        var noToolRequest = modelRequest(noTools);
        ReviewModel requestsTool = (pr, configuration, files) -> {
            files.read();
            return new ReviewModel.ReviewResult("Unauthorized tool call", List.of());
        };
        var unauthorized = new GraphExecutorResource(studioConfig, store, nodeCatalog, mapper, requestsTool);
        assertEquals(403, assertThrows(WebApplicationException.class,
                () -> unauthorized.execute(noToolRequest, "test-spike-executor")).getResponse().getStatus());
    }

    @Test
    void malformedModelOutputCannotReachPolicy() throws Exception {
        UUID publication = publish(fixture(), "0.0.1");
        var request = modelRequest(publication);
        ReviewModel invalidOutput = (pr, configuration, files) -> new ReviewModel.ReviewResult(null, List.of());
        var executor = new GraphExecutorResource(studioConfig, store, nodeCatalog, mapper, invalidOutput);
        assertEquals(502, assertThrows(WebApplicationException.class,
                () -> executor.execute(request, "test-spike-executor")).getResponse().getStatus());
    }

    private GraphExecutorResource.ExecuteRequest modelRequest(UUID publication) throws Exception {
        ReviewModel.PullRequest pr;
        try (var stream = getClass().getResourceAsStream("/studio/fixtures/pr-low-risk.json")) {
            pr = mapper.readValue(stream, ReviewModel.PullRequest.class);
        }
        return new GraphExecutorResource.ExecuteRequest(publication, store.createRun(publication), "review", "n_review",
                mapper.valueToTree(Map.of("pullRequest", pr)));
    }

    private JsonNode snapshot(UUID runId) throws Exception {
        return mapper.readTree(given().auth().oauth2(TOKEN).get("/api/graph-runs/" + runId)
                .then().statusCode(200).extract().asString());
    }

    private StudioGraph fixture() throws Exception {
        try (var stream = getClass().getResourceAsStream("/studio/examples/pr-review-basic.graph.json")) {
            var tree = (ObjectNode) mapper.readTree(stream);
            tree.put("flowId", UUID.randomUUID().toString());
            return mapper.treeToValue(tree, StudioGraph.class);
        }
    }

    private void invalid(String code, Consumer<ObjectNode> mutation) throws Exception {
        var tree = (ObjectNode) mapper.valueToTree(fixture());
        mutation.accept(tree);
        var validation = validator.validate(mapper.treeToValue(tree, StudioGraph.class));
        assertFalse(validation.valid(), code);
        assertTrue(validation.errors().stream().anyMatch(e -> code.equals(e.code())), validation.toString());
    }

    private UUID publish(StudioGraph graph, String version) throws Exception {
        String id = given().auth().oauth2(TOKEN).contentType("application/json")
                .body(Map.of("version", version, "graph", graph)).post("/api/graphs/publish")
                .then().statusCode(202).extract().jsonPath().getString("publicationId");
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!"READY".equals(given().auth().oauth2(TOKEN).get("/api/publications/" + id).jsonPath().getString("status"))) {
            assertTrue(System.nanoTime() < deadline, "Runner did not discover the compiled graph.");
            Thread.sleep(50);
        }
        return UUID.fromString(id);
    }

    private JsonNode run(UUID publication, String fixture) throws Exception {
        String runId = given().auth().oauth2(TOKEN).contentType("application/json").body(Map.of("fixture", fixture))
                .post("/api/publications/" + publication + "/runs").then().statusCode(202)
                .extract().jsonPath().getString("runId");
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (true) {
            JsonNode snapshot = mapper.readTree(given().auth().oauth2(TOKEN).get("/api/graph-runs/" + runId)
                    .then().statusCode(200).extract().asString());
            if (List.of("COMPLETED", "FAILED").contains(snapshot.path("status").asText())) {
                assertEquals("COMPLETED", snapshot.path("status").asText(), snapshot.toPrettyString());
                return snapshot;
            }
            assertTrue(System.nanoTime() < deadline, snapshot.toPrettyString());
            Thread.sleep(50);
        }
    }

    private static SetView executed(JsonNode snapshot) {
        var nodes = new HashSet<String>();
        snapshot.get("events").forEach(event -> {
            if ("TASK_STARTED".equals(event.path("kind").asText())) {
                nodes.add(event.path("nodeId").asText());
            }
        });
        return new SetView(nodes);
    }

    private static ArrayNode nodes(ObjectNode tree) {
        return (ArrayNode) tree.get("nodes");
    }

    private static ArrayNode edges(ObjectNode tree) {
        return (ArrayNode) tree.get("edges");
    }

    private record SetView(java.util.Set<String> nodes) {
    }
}
