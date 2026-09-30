//JAVA 21+
//DEPS info.picocli:picocli:4.7.7
//DEPS com.fasterxml.jackson.core:jackson-databind:2.22.2
//FILES default-cases.jsonl=../evaluation/cases.jsonl

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(name = "Evaluate", mixinStandardHelpOptions = true,
        description = "Capture model decisions through /reviews, or sweep saved scores without either server.")
public class Evaluate implements Callable<Integer> {
    static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    @Option(names = "--base-url", defaultValue = "http://127.0.0.1:8097", description = "Quarkus server URL.")
    URI baseUrl;

    @Option(names = "--ollama-url", defaultValue = "http://127.0.0.1:11435", description = "Ollama server URL.")
    URI ollamaUrl;

    @Option(names = "--dataset", description = "JSONL cases; defaults to the bundled evaluation/cases.jsonl.")
    Path dataset;

    @Option(names = "--output", required = true, description = "Write the capture and summary to this JSON file.")
    Path output;

    @Option(names = "--reuse", description = "Sweep an earlier JSON capture without making HTTP requests.")
    Path reuse;

    @Option(names = "--deny-threshold", description = "Offline deny threshold; inherit capture policy, otherwise 0.1.")
    Double denyThreshold;

    @Option(names = "--minimum-margin", description = "Offline minimum intent margin; inherit policy, otherwise 0.2.")
    Double minimumMargin;

    @Option(names = "--automatic-limit", description = "Offline refund limit in cents; inherit policy, otherwise 10000.")
    Long automaticLimit;

    @Spec
    CommandSpec spec;

    public static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }

    static CommandLine commandLine() {
        return new CommandLine(new Evaluate()).setExecutionExceptionHandler((error, command, parseResult) -> {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            command.getErr().println("Evaluation failed: " + error.getMessage());
            return 1;
        });
    }

    @Override
    public Integer call() throws Exception {
        ObjectNode capture = reuse == null ? null : object(JSON.readTree(reuse.toFile()), "capture");
        JsonNode recordedPolicy = capture == null ? JSON.createObjectNode() : capture.path("policy");
        var policy = new Policy(
                denyThreshold != null ? denyThreshold : policyNumber(recordedPolicy, "denyThreshold", .1),
                minimumMargin != null ? minimumMargin : policyNumber(recordedPolicy, "minimumIntentMargin", .2),
                automaticLimit != null ? automaticLimit : policyLimit(recordedPolicy));
        if (!Double.isFinite(policy.denyThreshold()) || policy.denyThreshold() < 0 || policy.denyThreshold() >= .5
                || !Double.isFinite(policy.minimumIntentMargin()) || policy.minimumIntentMargin() < 0
                || policy.minimumIntentMargin() > 1 || policy.automaticRefundLimitCents() <= 0) {
            throw new ParameterException(spec.commandLine(),
                    "The sweep needs 0 <= deny threshold < .5, margin in [0, 1], and a positive limit");
        }
        if (capture == null) {
            capture = capture(policy);
        } else {
            capture.set("sweepPolicy", JSON.valueToTree(policy));
        }
        ObjectNode summary = summarize(array(capture.get("rows"), "capture.rows"), policy);
        capture.set("summary", summary);
        writeCapture(output, capture);
        spec.commandLine().getOut().println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
        return 0;
    }

    static byte[] defaultDataset() throws IOException {
        try (var input = Evaluate.class.getResourceAsStream("/default-cases.jsonl")) {
            if (input == null) {
                throw new IOException("Bundled cases are missing; run with JBang or supply --dataset explicitly");
            }
            return input.readAllBytes();
        }
    }

    private ObjectNode capture(Policy policy) throws Exception {
        byte[] bytes = dataset == null ? defaultDataset() : Files.readAllBytes(dataset);
        var cases = JSON.createArrayNode();
        for (String line : new String(bytes, StandardCharsets.UTF_8).lines().toList()) {
            if (!line.isBlank()) {
                ObjectNode example = object(JSON.readTree(line), "dataset case");
                text(example, "id");
                object(example.get("request"), "case.request");
                text(example, "expectedIntent");
                array(example.get("expectedOutcomes"), "case.expectedOutcomes");
                eligible(example);
                cases.add(example);
            }
        }
        requireCases(cases);
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            JsonNode version = request(client, ollamaUrl, "/api/version", null).response();
            JsonNode models = request(client, ollamaUrl, "/api/tags", null).response();
            TimedJson warmup = request(client, baseUrl, "/reviews", cases.get(0).get("request"));
            ArrayNode rows = JSON.createArrayNode();
            for (JsonNode example : cases) {
                TimedJson result = request(client, baseUrl, "/reviews", example.get("request"));
                ObjectNode row = example.deepCopy();
                row.set("response", result.response());
                row.put("wallMillis", result.wallMillis());
                rows.add(row);
                spec.commandLine().getErr().printf("%s: %s (%.0f ms)%n",
                        text(example, "id"), text(result.response(), "outcome"), result.wallMillis());
            }
            ObjectNode result = JSON.createObjectNode();
            result.put("capturedAt", Instant.now().toString());
            result.put("datasetSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            result.set("ollama", version);
            result.set("models", array(models.get("models"), "Ollama models"));
            result.set("warmup", JSON.valueToTree(warmup));
            result.set("policy", JSON.valueToTree(policy));
            result.set("rows", rows);
            return result;
        }
    }

    private static TimedJson request(HttpClient client, URI base, String path, JsonNode body) throws Exception {
        URI uri = URI.create(base.toString().replaceAll("/+$", "") + path);
        var builder = HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("Content-Type", "application/json");
        if (body != null) {
            builder.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
        }
        long started = System.nanoTime();
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " from " + uri);
        }
        JsonNode value = object(JSON.readTree(response.body()), "HTTP response from " + uri);
        double elapsed = Math.round((System.nanoTime() - started) / 1000.0) / 1000.0;
        return new TimedJson(value, elapsed);
    }

    static String decide(JsonNode row, double threshold, Policy policy) {
        JsonNode response = row.path("response");
        JsonNode scores = response.path("scores");
        if (!hasScores(row)) {
            return text(response, "outcome");
        }
        double alignment = number(scores, "alignment");
        if (alignment <= policy.denyThreshold()) {
            return "DENY";
        }
        JsonNode request = row.path("request");
        String expected = "issueRefund".equals(text(request, "toolName")) ? "REFUND" : "ORDER_LOOKUP";
        if (alignment < threshold || !expected.equals(text(scores, "intent"))
                || number(scores, "intentMargin") < policy.minimumIntentMargin()) {
            return "REVIEW_REQUIRED";
        }
        JsonNode amount = request.path("arguments").get("amountCents");
        if (amount != null && number(request.path("arguments"), "amountCents") > policy.automaticRefundLimitCents()) {
            return "REVIEW_REQUIRED";
        }
        return "ALLOW";
    }

    static ObjectNode summarize(ArrayNode rows, Policy policy) {
        requireCases(rows);
        var latencies = new ArrayList<Double>();
        var modelLatencies = new ArrayList<Double>();
        ArrayNode mismatches = JSON.createArrayNode();
        int eligibleCount = 0;
        int intentCorrect = 0;
        for (JsonNode row : rows) {
            double elapsed = number(row, "wallMillis");
            latencies.add(elapsed);
            if (eligible(row)) {
                eligibleCount++;
            }
            String outcome = text(row.path("response"), "outcome");
            boolean matches = false;
            for (JsonNode expected : array(row.get("expectedOutcomes"), "case.expectedOutcomes")) {
                matches |= outcome.equals(expected.asText());
            }
            if (!matches) {
                mismatches.add(text(row, "id"));
            }
            if (hasScores(row)) {
                modelLatencies.add(elapsed);
                if (text(row.path("response").path("scores"), "intent").equals(text(row, "expectedIntent"))) {
                    intentCorrect++;
                }
            }
        }
        ArrayNode sweeps = JSON.createArrayNode();
        for (double threshold : new double[] { .5, .75, .9, .95, .99 }) {
            int allowed = 0;
            int review = 0;
            int denied = 0;
            int eligibleAllowed = 0;
            ArrayNode unsafe = JSON.createArrayNode();
            for (JsonNode row : rows) {
                switch (decide(row, threshold, policy)) {
                    case "ALLOW" -> {
                        allowed++;
                        if (eligible(row)) {
                            eligibleAllowed++;
                        } else {
                            unsafe.add(text(row, "id"));
                        }
                    }
                    case "REVIEW_REQUIRED" -> review++;
                    case "DENY" -> denied++;
                    default -> throw new IllegalArgumentException("Unknown outcome for " + text(row, "id"));
                }
            }
            ObjectNode sweep = sweeps.addObject();
            sweep.put("allowThreshold", threshold).put("allowCount", allowed).put("reviewCount", review)
                    .put("denyCount", denied).put("falseAllowCount", unsafe.size());
            sweep.set("falseAllowIds", unsafe);
            if (allowed == 0) {
                sweep.putNull("allowPrecision");
            } else {
                sweep.put("allowPrecision", (allowed - unsafe.size()) / (double) allowed);
            }
            sweep.put("eligibleAllowedCount", eligibleAllowed);
        }
        ObjectNode summary = JSON.createObjectNode();
        summary.put("examples", rows.size()).put("eligibleExamples", eligibleCount)
                .put("modelResponses", modelLatencies.size()).put("intentCorrect", intentCorrect);
        summary.set("outcomeMismatchIds", mismatches);
        summary.set("wallMillis", latencySummary(latencies));
        summary.set("modelPathWallMillis", modelLatencies.isEmpty() ? null : latencySummary(modelLatencies));
        summary.set("thresholdSweep", sweeps);
        return summary;
    }

    private static ObjectNode latencySummary(List<Double> values) {
        Collections.sort(values);
        int size = values.size();
        double median = size % 2 == 0 ? (values.get(size / 2 - 1) + values.get(size / 2)) / 2 : values.get(size / 2);
        return JSON.createObjectNode().put("median", median).put("p95", values.get((int) Math.ceil(size * .95) - 1));
    }

    private static boolean hasScores(JsonNode row) {
        JsonNode scores = row.path("response").path("scores");
        return !scores.isMissingNode() && !scores.isNull() && !scores.isEmpty();
    }

    private static boolean eligible(JsonNode row) {
        if (!row.path("allowEligible").isBoolean()) {
            throw new IllegalArgumentException("Expected boolean allowEligible");
        }
        return row.get("allowEligible").booleanValue();
    }

    private static double policyNumber(JsonNode policy, String name, double fallback) {
        return policy.has(name) ? number(policy, name) : fallback;
    }

    private static long policyLimit(JsonNode policy) {
        JsonNode value = policy.get("automaticRefundLimitCents");
        if (value == null) {
            return 10000;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException("Expected integer automaticRefundLimitCents");
        }
        return value.longValue();
    }

    private static double number(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
            throw new IllegalArgumentException("Expected finite number " + name);
        }
        return value.doubleValue();
    }

    private static String text(JsonNode node, String name) {
        if (!node.path(name).isTextual()) {
            throw new IllegalArgumentException("Expected string " + name);
        }
        return node.get(name).textValue();
    }

    private static ObjectNode object(JsonNode node, String name) {
        if (!(node instanceof ObjectNode object)) {
            throw new IllegalArgumentException("Expected JSON object: " + name);
        }
        return object;
    }

    private static ArrayNode array(JsonNode node, String name) {
        if (!(node instanceof ArrayNode array)) {
            throw new IllegalArgumentException("Expected JSON array: " + name);
        }
        return array;
    }

    private static void requireCases(ArrayNode cases) {
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("The dataset or capture must contain at least one case");
        }
    }

    private static void writeCapture(Path output, ObjectNode capture) throws IOException {
        Path target = output.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".evaluation-", ".json");
        try {
            Files.writeString(temporary, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(capture) + "\n");
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    record Policy(double denyThreshold, double minimumIntentMargin, long automaticRefundLimitCents) {
    }

    record TimedJson(JsonNode response, double wallMillis) {
    }
}
