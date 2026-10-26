package com.themainthread.studio.graph;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themainthread.studio.StudioAccessFilter;
import com.themainthread.studio.StudioConfig;

import io.smallrye.common.annotation.Blocking;

@Path("/internal/graph/execute")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class GraphExecutorResource {
    private final StudioConfig config;
    private final GraphStore store;
    private final NodeCatalog catalog;
    private final ObjectMapper mapper;
    private final ReviewModel model;

    public GraphExecutorResource(StudioConfig config, GraphStore store, NodeCatalog catalog,
            ObjectMapper mapper, ReviewModel model) {
        this.config = config;
        this.store = store;
        this.catalog = catalog;
        this.mapper = mapper;
        this.model = model;
    }

    @POST
    public Map<String, Object> execute(ExecuteRequest request,
            @HeaderParam("X-Studio-Internal-Token") String token) throws Exception {
        if (!StudioAccessFilter.matches(config.internalToken(), token)) {
            throw new WebApplicationException(401);
        }
        if (request == null || request.publicationId() == null || request.runId() == null || request.inputs() == null
                || !request.inputs().isObject()) {
            throw new BadRequestException("Provide a bounded node execution envelope.");
        }
        var run = store.run(request.runId());
        if (!run.publicationId().equals(request.publicationId()) || "START_UNCERTAIN".equals(run.startStatus())) {
            throw new WebApplicationException(403);
        }
        var publication = store.get(request.publicationId());
        var node = publication.compiled().manifest().get(request.taskId());
        if (!"READY".equals(publication.status()) || node == null || !node.nodeId().equals(request.nodeId())) {
            throw new WebApplicationException(403);
        }
        var actual = new HashSet<String>();
        request.inputs().fieldNames().forEachRemaining(actual::add);
        var expected = new HashSet<String>();
        catalog.get(node.type()).inputPorts().forEach(port -> expected.add(port.id()));
        if (!actual.equals(expected) || mapper.writeValueAsBytes(request.inputs()).length > 16384) {
            throw new BadRequestException("The node inputs must match the published ports and size limit.");
        }
        return switch (node.type()) {
            case "task.fetch-pr-fixture" -> Map.of("pullRequest", fixture(request.inputs().get("fixture")));
            case "ai.agent" -> Map.of("reviewResult", review(request.inputs().get("pullRequest"), node.agent()));
            case "task.review-policy" -> {
                var review = mapper.treeToValue(request.inputs().get("reviewResult"), ReviewModel.ReviewResult.class);
                var pr = pullRequest(request.inputs().get("pullRequest"));
                validReview(review);
                var minimum = ReviewModel.Severity.valueOf(node.config().get("minimumSeverity").asText());
                boolean sensitivePath = pr.files().stream()
                        .anyMatch(f -> f.path().startsWith("security/") || f.path().contains("/security/"));
                boolean requires = sensitivePath || review.findings().stream()
                        .anyMatch(f -> f.severity().ordinal() >= minimum.ordinal());
                yield Map.of("requiresApproval", requires);
            }
            default -> throw new BadRequestException("This node is not an HTTP executor.");
        };
    }

    private ReviewModel.PullRequest fixture(JsonNode name) throws Exception {
        if (name == null || !name.isTextual() || !java.util.Set.of("low-risk", "high-risk").contains(name.asText())) {
            throw new BadRequestException("Use a registered PR fixture.");
        }
        try (var stream = getClass().getResourceAsStream("/studio/fixtures/pr-" + name.asText() + ".json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing registered PR fixture.");
            }
            return mapper.readValue(stream, ReviewModel.PullRequest.class);
        }
    }

    private ReviewModel.PullRequest pullRequest(JsonNode value) throws Exception {
        var pr = mapper.treeToValue(value, ReviewModel.PullRequest.class);
        if (pr == null || pr.title() == null || pr.title().length() > 500 || pr.files() == null
                || pr.files().isEmpty() || pr.files().size() > 10
                || pr.files().stream().anyMatch(f -> f == null || f.path() == null || f.path().length() > 300
                        || f.diff() == null || f.diff().length() > 4000)) {
            throw new BadRequestException("Invalid bounded PR contract.");
        }
        return pr;
    }

    private ReviewModel.ReviewResult review(JsonNode input, GraphCompiler.AgentConfiguration agent) throws Exception {
        var pr = pullRequest(input);
        if (agent == null || !"fixture-model-v1".equals(agent.modelRef())
                || !"review-result-v1".equals(agent.outputSchemaRef())) {
            throw new WebApplicationException(403);
        }
        var calls = new AtomicInteger();
        long started = System.nanoTime();
        var result = model.review(pr, agent, () -> {
            if (!agent.allowedToolIds().contains("pr.read-files") || calls.incrementAndGet() > agent.maxToolCalls()) {
                throw new WebApplicationException("Tool budget or permission exceeded.", 403);
            }
            return pr.files();
        });
        if (System.nanoTime() - started > java.time.Duration.ofMillis(agent.modelTimeoutMs()).toNanos()) {
            throw new WebApplicationException("Fixture model exceeded its budget.", 504);
        }
        validReview(result);
        if (mapper.writeValueAsBytes(result).length > agent.maxOutputBytes()) {
            throw new WebApplicationException("Model output exceeded its size budget.", 502);
        }
        return result;
    }

    private static void validReview(ReviewModel.ReviewResult result) {
        if (result == null || result.summary() == null || result.summary().length() > 1000
                || result.findings() == null || result.findings().size() > 20
                || result.findings().stream().anyMatch(f -> f == null || f.file() == null || f.file().length() > 300
                        || f.line() < 1 || f.severity() == null || f.category() == null || f.category().length() > 80
                        || f.message() == null || f.message().length() > 1000)) {
            throw new WebApplicationException("Invalid structured review output.", 502);
        }
    }

    public record ExecuteRequest(UUID publicationId, UUID runId, String nodeId, String taskId, JsonNode inputs) {
    }
}
