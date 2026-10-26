package com.themainthread.studio.graph;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themainthread.studio.EventJournal;
import com.themainthread.studio.StudioAccess;
import com.themainthread.studio.StudioConfig;

import io.smallrye.common.annotation.Blocking;

@Path("/api")
@StudioAccess
@Blocking
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GraphResource {
    private final NodeCatalog catalog;
    private final GraphValidator validator;
    private final GraphCompiler compiler;
    private final GraphPublisher publisher;
    private final GraphStore store;
    private final ObjectMapper mapper;
    private final StudioConfig config;
    private final EventJournal journal;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public GraphResource(NodeCatalog catalog, GraphValidator validator, GraphCompiler compiler, GraphPublisher publisher,
            GraphStore store, ObjectMapper mapper, StudioConfig config, EventJournal journal) {
        this.catalog = catalog;
        this.validator = validator;
        this.compiler = compiler;
        this.publisher = publisher;
        this.store = store;
        this.mapper = mapper;
        this.config = config;
        this.journal = journal;
    }

    @GET
    @Path("/node-types")
    public List<NodeCatalog.Descriptor> nodeTypes() {
        return catalog.all();
    }

    @GET
    @Path("/graph-examples/pr-review")
    public StudioGraph example() throws Exception {
        try (var stream = getClass().getResourceAsStream("/studio/examples/pr-review-basic.graph.json")) {
            return mapper.readValue(stream, StudioGraph.class);
        }
    }

    @POST
    @Path("/graphs/validate")
    public GraphValidator.Validation validate(StudioGraph graph) {
        return validator.validate(graph);
    }

    @POST
    @Path("/graphs/compile")
    public GraphCompiler.Compiled compile(PublishRequest request) throws Exception {
        if (request == null) {
            throw new BadRequestException();
        }
        return compiler.compile(request.graph(), request.version());
    }

    @POST
    @Path("/graphs/publish")
    public Response publish(PublishRequest request) throws Exception {
        if (request == null) {
            throw new BadRequestException();
        }
        return Response.accepted(metadata(publisher.publish(request.graph(), request.version()))).build();
    }

    @GET
    @Path("/publications/{id}")
    public Map<String, Object> publication(@PathParam("id") UUID id) throws Exception {
        return metadata(publisher.publication(id));
    }

    @GET
    @Path("/publications/{id}/compiled")
    public GraphCompiler.Compiled compiled(@PathParam("id") UUID id) throws Exception {
        return store.get(id).compiled();
    }

    @POST
    @Path("/publications/{id}/runs")
    public Response run(@PathParam("id") UUID id, RunRequest input) throws Exception {
        if (input == null || input.fixture() == null || !Set.of("low-risk", "high-risk").contains(input.fixture())) {
            throw new BadRequestException("Select low-risk or high-risk.");
        }
        var compiled = publisher.ready(id);
        UUID runId = store.createRun(id);
        try {
            var body = mapper.writeValueAsString(Map.of("runId", runId, "fixture", input.fixture()));
            var request = HttpRequest.newBuilder(URI.create(config.executorBaseUrl() + "/q/flow/exec/studio/"
                    + compiled.workflowName() + "/" + compiled.version())).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + config.runnerToken()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var accepted = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (accepted.statusCode() != 202) {
                throw new IllegalStateException("Runner did not accept execution.");
            }
            String instance = mapper.readTree(accepted.body()).path("instanceId").asText();
            if (!instance.matches("[A-Z0-9]{26}")) {
                throw new IllegalStateException("Runner returned an invalid instance identity.");
            }
            store.started(runId, instance, "RUNNING");
            return Response.accepted(Map.of("runId", runId, "instanceId", instance, "publicationId", id,
                    "version", compiled.version(), "status", "RUNNING", "runUrl", "/api/graph-runs/" + runId)).build();
        } catch (Exception e) {
            store.started(runId, null, "START_UNCERTAIN");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new WebApplicationException(Response.status(503).entity(Map.of("code", "RUN_START_UNCERTAIN",
                    "runId", runId, "message", "The start outcome is uncertain. Do not retry automatically.")).build());
        }
    }

    @GET
    @Path("/graph-runs/{id}")
    public Map<String, Object> run(@PathParam("id") UUID id) throws Exception {
        var run = store.run(id);
        var compiled = store.get(run.publicationId()).compiled();
        var events = new ArrayList<Map<String, Object>>();
        String status = run.startStatus();
        JsonNode output = null;
        for (var event : journal.eventsForRun(id)) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("eventId", event.eventId());
            entry.put("kind", event.kind());
            entry.put("taskId", event.task());
            var node = event.task() == null ? null : compiled.manifest().get(event.task());
            entry.put("nodeId", node == null ? null : node.nodeId());
            entry.put("runId", event.runId());
            entry.put("version", event.version());
            entry.put("occurredAt", event.occurredAt());
            if ("TASK_FAILED".equals(event.kind())) {
                entry.put("error", event.output());
            }
            events.add(entry);
            if ("WORKFLOW_COMPLETED".equals(event.kind())) {
                status = "COMPLETED";
                output = event.output();
            } else if ("WORKFLOW_FAILED".equals(event.kind())) {
                status = "FAILED";
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("runId", id);
        result.put("instanceId", run.instanceId());
        result.put("publicationId", run.publicationId());
        result.put("version", compiled.version());
        result.put("status", status);
        result.put("events", events);
        result.put("output", output);
        result.put("lostEvents", journal.lostEvents());
        return result;
    }

    private static Map<String, Object> metadata(GraphStore.Publication publication) {
        var compiled = publication.compiled();
        return Map.of("publicationId", compiled.publicationId(), "flowId", compiled.flowId(), "version", compiled.version(),
                "status", publication.status(), "graphSha256", compiled.graphSha256(),
                "artifactSha256", compiled.artifactSha256(), "compilerVersion", compiled.compilerVersion());
    }

    @PreDestroy
    void close() {
        client.close();
    }

    public record PublishRequest(String version, StudioGraph graph) {
    }

    public record RunRequest(String fixture) {
    }
}
