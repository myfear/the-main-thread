//JAVA 21+
//SOURCES CompareQuestions.java
//FILES default-cases.jsonl=../evaluation/cases.jsonl
//FILES baseline.json=../evaluation/question-design/baseline.json
//FILES split-v1.json=../evaluation/question-design/split-v1.json
//FILES split-v2.json=../evaluation/question-design/split-v2.json
//DEPS com.fasterxml.jackson.core:jackson-databind:2.22.2
//DEPS info.picocli:picocli:4.7.7

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import picocli.CommandLine;

/** Offline compatibility tests; HTTP requests go only to the local stub below. */
public class CompareQuestionsTest {
    private static Path root;
    private static final List<String> VARIANTS = List.of("baseline", "split-v1", "split-v2");
    private static final Map<String, JsonNode> SPECS = new LinkedHashMap<>();

    @FunctionalInterface
    interface Check {
        void run() throws Exception;
    }

    record Result(int status, String out, String err) {
    }

    record Request(String path, String method, JsonNode body) {
    }

    public static void main(String[] args) throws Exception {
        root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        for (String name : VARIANTS) {
            SPECS.put(name, CompareQuestions.JSON.readTree(CompareQuestions.resource(name + ".json")));
        }
        var suite = new CompareQuestionsTest();
        Map<String, Check> checks = new LinkedHashMap<>();
        checks.put("deterministic validation rejects malformed proposals", suite::invalidProposals);
        checks.put("money normalization preserves exact cents without mutating input", suite::moneyState);
        checks.put("lookup and baseline state omit normalized money", suite::unmodifiedState);
        checks.put("every frozen question set agrees with its required checks", suite::questionSets);
        checks.put("lookup does not require a refund amount answer", suite::lookupChecks);
        checks.put("required check thresholds apply independently", suite::thresholds);
        checks.put("conflicting intent and small margin require review", suite::intentAndMargin);
        checks.put("automatic refund cap is inclusive", suite::refundLimit);
        checks.put("missing or malformed model structure fails closed", suite::responseStructure);
        checks.put("invalid and nonfinite check scores fail closed", suite::checkScores);
        checks.put("inconsistent intent distributions fail closed", suite::distributions);
        checks.put("summary handles absent responses and missing token usage", suite::summaryStatistics);
        checks.put("invalid proposals are never sent for warmup or scoring", suite::invalidProposalsStayLocal);
        checks.put("all 276 saved rows and 240 responses preserve assessments and summaries", suite::savedCaptureParity);
        checks.put("bundled dataset and all variants match source bytes", suite::resources);
        checks.put("HTTP capture preserves provenance and interleaves reversed repetitions", suite::liveCapture);
        checks.put("malformed model answers are captured as review required", suite::invalidModelCapture);
        checks.put("invalid flags and inputs fail without overwriting output", suite::invalidInputs);
        checks.put("HTTP and JSON failures preserve existing output", suite::httpFailures);
        checks.put("production JBang entry point loads defaults outside the module", suite::productionEntryPoint);
        ArrayNode results = CompareQuestions.JSON.createArrayNode();
        int failures = 0;
        for (var entry : checks.entrySet()) {
            ObjectNode result = results.addObject().put("test", entry.getKey());
            try {
                entry.getValue().run();
                result.put("result", "PASS");
            } catch (Exception | AssertionError e) {
                failures++;
                result.put("result", "FAIL").put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        ObjectNode report = object().put("tests", checks.size()).put("passed", checks.size() - failures)
                .put("failed", failures).put("savedRowsReplayed", 276).put("savedResponsesReplayed", 240)
                .put("liveModelCalls", 0).put("projectRoot", root.toString());
        report.set("results", results);
        // Machine-readable test output, not application logging.
        CompareQuestions.JSON.writerWithDefaultPrettyPrinter().writeValue(System.out, report);
        if (failures > 0) {
            System.exit(1);
        }
    }

    void invalidProposals() throws Exception {
        List<JsonNode> invalid = new ArrayList<>(List.of(node(null), array(), object()));
        for (Object value : Arrays.asList(null, " ", "x".repeat(2001))) {
            invalid.add(replace(refund(), "userRequest", value));
        }
        for (Object value : List.of("deleteOrder", List.of())) {
            invalid.add(replace(refund(), "toolName", value));
        }
        for (Object value : Arrays.asList(null, List.of())) {
            invalid.add(replace(refund(), "arguments", value));
        }
        for (Object value : List.of(4712, List.of(), "9999", "8421")) {
            ObjectNode request = refund();
            replace(request.withObject("arguments"), "orderId", value);
            invalid.add(request);
        }
        for (Object value : Arrays.asList(null, true, "100", 1.5, 0, -1, 2000, Double.NaN,
                Double.POSITIVE_INFINITY, BigInteger.TEN.pow(400))) {
            ObjectNode request = refund();
            replace(request.withObject("arguments"), "amountCents", value);
            invalid.add(request);
        }
        ObjectNode missing = refund();
        missing.withObject("arguments").remove("amountCents");
        invalid.add(missing);
        ObjectNode extra = refund();
        extra.withObject("arguments").put("approved", true);
        invalid.add(extra);
        ObjectNode extraLookup = lookup();
        extraLookup.withObject("arguments").put("amountCents", 100);
        invalid.add(extraLookup);
        for (JsonNode request : invalid) {
            check(CompareQuestions.stateFor(request, spec()) == null, "Invalid proposal produced a state: " + request);
            ObjectNode assessment = CompareQuestions.classify(request, response("issueRefund", spec()), spec());
            equal("DENY", assessment.path("outcome").asText(), "invalid proposal outcome");
            equal("DETERMINISTIC_REJECTION", assessment.path("reason").asText(), "invalid proposal reason");
        }
    }

    void moneyState() {
        Map<Integer, String> money = Map.of(1, "0.01", 7, "0.07", 1999, "19.99", 10000, "100.00", 49900, "499.00");
        for (var entry : money.entrySet()) {
            ObjectNode request = refund();
            request.withObject("arguments").put("orderId", "4711").put("amountCents", entry.getKey());
            JsonNode original = request.deepCopy();
            ObjectNode state = CompareQuestions.stateFor(request, spec());
            equal(entry.getValue(), state.path("proposedAmountEuros").asText(), "exact decimal money");
            equal("499.00", state.at("/order/paidEuros").asText(), "paid decimal money");
            equal(entry.getKey(), state.at("/arguments/amountCents").asInt(), "original cents");
            equal("EUR", state.at("/order/currency").asText(), "currency");
            sameJson(original, request, "request preserved");
            check(state.get("arguments") != request.get("arguments"), "state must copy arguments");
            state.withObject("arguments").put("amountCents", -1);
            sameJson(original, request, "state mutation must not change request");
        }
    }

    void unmodifiedState() {
        for (JsonNode state : List.of(CompareQuestions.stateFor(lookup(), spec()),
                CompareQuestions.stateFor(refund(), SPECS.get("baseline")))) {
            check(!state.has("proposedAmountEuros"), "No normalized proposed money on lookup or baseline");
            check(!state.get("order").has("paidEuros"), "No normalized order money on lookup or baseline");
        }
    }

    void questionSets() {
        for (var entry : SPECS.entrySet()) {
            JsonNode spec = entry.getValue();
            for (String tool : List.of("getOrder", "issueRefund")) {
                JsonNode required = spec.path("requiredChecks").path(tool);
                JsonNode questions = spec.path("questions").path(tool);
                equal(required.size() + 1, questions.size(), entry.getKey() + " question count");
                equal("choice", questions.at("/intent/type").asText(), "intent question type");
                for (JsonNode name : required) {
                    equal("noul", questions.path(name.asText()).path("type").asText(), "required question type");
                }
                equal("ALLOW", CompareQuestions.classify(tool.equals("getOrder") ? lookup() : refund(),
                        response(tool, spec), spec).path("outcome").asText(), entry.getKey() + " " + tool);
            }
        }
        sameJson(node(List.of("orderMatches")), spec().at("/requiredChecks/getOrder"), "lookup checks");
        sameJson(node(List.of("orderMatches", "amountMatches")), spec().at("/requiredChecks/issueRefund"), "refund checks");
    }

    void lookupChecks() {
        ObjectNode response = response("getOrder", spec());
        check(!response.path("answers").has("amountMatches"), "lookup answer excludes amount");
        equal("ALLOW", CompareQuestions.classify(lookup(), response, spec()).path("outcome").asText(), "lookup outcome");
        response.withObject("answers").remove("orderMatches");
        invalidResponse(response, lookup());
    }

    void thresholds() {
        for (String name : List.of("orderMatches", "amountMatches")) {
            for (double score : new double[] {0, .1, .10001, .89999, .9, 1}) {
                ObjectNode response = response("issueRefund", spec());
                response.withObject("answers").withObject(name).put("noul", score);
                String expected = score <= .1 ? "DENY" : score < .9 ? "REVIEW_REQUIRED" : "ALLOW";
                equal(expected, CompareQuestions.classify(refund(), response, spec()).path("outcome").asText(), name + " " + score);
            }
        }
        ObjectNode response = response("issueRefund", spec());
        response.withObject("answers").withObject("orderMatches").put("noul", .9);
        response.withObject("answers").withObject("amountMatches").put("noul", .9);
        equal("ALLOW", CompareQuestions.classify(refund(), response, spec()).path("outcome").asText(),
                "individual checks, not a product or average");
    }

    void intentAndMargin() {
        ObjectNode response = response("issueRefund", spec());
        response.withObject("answers").set("intent", response("getOrder", spec()).at("/answers/intent"));
        equal("REVIEW_REQUIRED", CompareQuestions.classify(refund(), response, spec()).path("outcome").asText(), "intent mismatch");
        for (double[] probabilities : List.of(new double[] {.55, .4, .05}, new double[] {.5, .3, .2})) {
            response = response("issueRefund", spec());
            response.withObject("answers").withObject("intent").set("probabilities", object()
                    .put("REFUND", probabilities[0]).put("ORDER_LOOKUP", probabilities[1]).put("OTHER", probabilities[2]));
            equal(probabilities[0] == .55 ? "REVIEW_REQUIRED" : "ALLOW",
                    CompareQuestions.classify(refund(), response, spec()).path("outcome").asText(), "intent margin boundary");
        }
    }

    void refundLimit() {
        for (int amount : new int[] {9999, 10000, 10001, 49900}) {
            ObjectNode request = refund();
            request.withObject("arguments").put("orderId", "4711").put("amountCents", amount);
            equal(amount > 10000 ? "REVIEW_REQUIRED" : "ALLOW",
                    CompareQuestions.classify(request, response("issueRefund", spec()), spec()).path("outcome").asText(), "refund cap");
        }
    }

    void responseStructure() throws Exception {
        for (String text : List.of("null", "[]", "{}", "{\"answers\":null}", "{\"answers\":[]}",
                "{\"answers\":{}}", "{\"answers\":{\"intent\":null}}", "{\"answers\":{\"intent\":{}}}")) {
            invalidResponse(CompareQuestions.JSON.readTree(text), refund());
        }
        invalidResponse(null, refund());
    }

    void checkScores() {
        for (String name : List.of("orderMatches", "amountMatches")) {
            for (Object value : Arrays.asList(null, true, false, "0.99", Double.NaN, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, -.01, 1.01, BigInteger.TEN.pow(400))) {
                ObjectNode response = response("issueRefund", spec());
                replace(response.withObject("answers").withObject(name), "noul", value);
                invalidResponse(response, refund());
            }
            for (JsonNode value : List.of(node(null), object(), object().put("type", "choice").put("noul", .99))) {
                ObjectNode response = response("issueRefund", spec());
                response.withObject("answers").set(name, value);
                invalidResponse(response, refund());
            }
            ObjectNode response = response("issueRefund", spec());
            response.withObject("answers").remove(name);
            invalidResponse(response, refund());
        }
    }

    void distributions() {
        List<JsonNode> invalid = new ArrayList<>(List.of(node(null), array(), object(), object().put("REFUND", 1),
                object().put("REFUND", .97).put("ORDER_LOOKUP", .01).put("OTHER", .01).put("UNKNOWN", .01),
                object().put("REFUND", .9).put("ORDER_LOOKUP", .3).put("OTHER", .1),
                object().put("REFUND", .7).put("ORDER_LOOKUP", .01).put("OTHER", .01),
                object().put("REFUND", .01).put("ORDER_LOOKUP", .98).put("OTHER", .01)));
        for (Object score : Arrays.asList(null, true, "0.98", Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, -.01, 1.01)) {
            invalid.add(replace(object().put("ORDER_LOOKUP", .01).put("OTHER", .01), "REFUND", score));
        }
        for (JsonNode probabilities : invalid) {
            ObjectNode response = response("issueRefund", spec());
            response.withObject("answers").withObject("intent").set("probabilities", probabilities);
            invalidResponse(response, refund());
        }
        for (var change : Map.of("choice", node("UNKNOWN"), "type", node("noul"), "confidence", node(Double.NaN)).entrySet()) {
            ObjectNode response = response("issueRefund", spec());
            response.withObject("answers").withObject("intent").set(change.getKey(), change.getValue());
            invalidResponse(response, refund());
        }
        ObjectNode response = response("issueRefund", spec());
        response.withObject("answers").withObject("intent").set("choice", array());
        invalidResponse(response, refund());
    }

    void summaryStatistics() {
        ObjectNode empty = CompareQuestions.summarize(array());
        equal(0, empty.path("modelResponses").asInt(), "empty response count");
        for (String name : List.of("medianWallMillis", "p95WallMillis", "meanInputTokens")) {
            check(empty.path(name).isNull(), "empty " + name);
        }
        for (JsonNode raw : List.of(node(null), object(), object().putNull("usage"),
                object().set("usage", object().put("input_tokens", Double.NaN)))) {
            ObjectNode row = testCase("invalid", refund(), true, "REFUND", "ALLOW");
            row.put("outcome", "REVIEW_REQUIRED").put("wallMillis", 5);
            row.set("raw", raw);
            row.set("assessment", CompareQuestions.classify(refund(), raw, spec()));
            ObjectNode summary = CompareQuestions.summarize(array().add(row));
            sameJson(node(List.of("invalid")), summary.get("mismatchIds"), "mismatch id");
            equal(0, summary.path("intentCorrect").asInt(), "invalid intent count");
            check(summary.path("meanInputTokens").isNull(), "invalid token count omitted");
        }
        ArrayNode rows = array();
        for (int i = 1; i <= 20; i++) {
            ObjectNode row = testCase("row-" + i, refund(), true, "REFUND", "ALLOW");
            row.put("outcome", "ALLOW").put("wallMillis", i);
            ObjectNode response = response("issueRefund", spec());
            response.withObject("usage").put("input_tokens", i);
            row.set("raw", response);
            row.set("assessment", CompareQuestions.classify(refund(), response, spec()));
            rows.add(row);
        }
        ObjectNode even = CompareQuestions.summarize(rows);
        equal(10.5, even.path("medianWallMillis").asDouble(), "even median");
        equal(19, even.path("p95WallMillis").asDouble(), "nearest-rank p95");
        equal(10.5, even.path("meanInputTokens").asDouble(), "token mean");
        rows.remove(19);
        equal(10, CompareQuestions.summarize(rows).path("medianWallMillis").asDouble(), "odd median");
    }

    void invalidProposalsStayLocal() throws Exception {
        for (boolean includeValid : List.of(false, true)) {
            Path directory = Files.createTempDirectory("question-local-rejections-");
            ObjectNode invalid = testCase("invalid", refund().put("toolName", "deleteOrder"), false, "OTHER", "DENY");
            String data = invalid + "\n";
            if (includeValid) {
                data += testCase("valid", refund(), true, "REFUND", "ALLOW") + "\n";
            }
            Path dataset = directory.resolve("cases.jsonl");
            Files.writeString(dataset, data);
            Path output = directory.resolve("capture.json");
            try (FakeServer server = new FakeServer("normal")) {
                success(cli("--dataset", dataset.toString(), "--variants", "split-v2", "--output", output.toString(),
                        "--ollama-url", server.url()));
                ObjectNode capture = read(output);
                equal(includeValid ? 2 : 0, server.scored().size(), "warmup and scoring count");
                equal("DENY", capture.at("/runs/0/rows/0/outcome").asText(), "deterministic rejection");
                check(!capture.at("/runs/0/rows/0").has("raw"), "rejected proposal has no response");
                equal(includeValid ? 1 : 0, capture.at("/runs/0/summary/modelResponses").asInt(), "modeled rows");
            }
        }
    }

    void savedCaptureParity() throws Exception {
        int rows = 0;
        int responses = 0;
        for (String file : List.of("development-comparison.json", "development-split-v2.json", "holdout-comparison.json")) {
            ObjectNode capture = read(root.resolve("evaluation/question-design").resolve(file));
            for (JsonNode run : capture.path("runs")) {
                JsonNode spec = capture.path("specs").path(run.path("variant").asText());
                for (JsonNode row : run.path("rows")) {
                    rows++;
                    String label = file + "/" + run.path("variant").asText() + "/" + run.path("repeat").asInt()
                            + "/" + row.path("id").asText();
                    JsonNode state = CompareQuestions.stateFor(row.get("request"), spec);
                    if (state == null) {
                        equal("DENY", row.path("outcome").asText(), label);
                        check(!row.has("raw"), label + " rejected without a model call");
                    } else {
                        responses++;
                        sameJson(row.at("/payload/state"), state, label + " state");
                        sameJson(row.at("/payload/questions"), spec.path("questions").path(row.at("/request/toolName").asText()),
                                label + " questions");
                        sameJson(row.get("assessment"), CompareQuestions.classify(row.get("request"), row.get("raw"), spec),
                                label + " assessment");
                    }
                }
                sameJson(run.get("summary"), CompareQuestions.summarize((ArrayNode) run.get("rows")), file + " summary");
            }
        }
        equal(276, rows, "saved row coverage");
        equal(240, responses, "saved model response coverage");
    }

    void resources() throws Exception {
        check(Arrays.equals(Files.readAllBytes(root.resolve("evaluation/cases.jsonl")),
                CompareQuestions.resource("default-cases.jsonl")), "bundled dataset bytes");
        for (String name : VARIANTS) {
            check(Arrays.equals(Files.readAllBytes(root.resolve("evaluation/question-design/" + name + ".json")),
                    CompareQuestions.resource(name + ".json")), "bundled " + name + " bytes");
        }
    }

    void liveCapture() throws Exception {
        Path directory = Files.createTempDirectory("question-live-");
        ObjectNode first = testCase("first", refund(), true, "REFUND", "ALLOW").put("customLabel", "preserved");
        ObjectNode second = testCase("second", lookup(), true, "ORDER_LOOKUP", "ALLOW");
        ObjectNode invalid = testCase("invalid", refund().put("toolName", "deleteOrder"), false, "OTHER", "DENY");
        String data = first + "\n\n" + invalid + "\n" + second + "\n";
        Path dataset = directory.resolve("interleaved.jsonl");
        Files.writeString(dataset, data);
        Path output = directory.resolve("nested/capture.json");
        try (FakeServer server = new FakeServer("normal")) {
            success(cli("--dataset", dataset.toString(), "--variants", "baseline", "split-v1", "split-v2",
                    "--repeats", "2", "--output", output.toString(), "--ollama-url", server.url() + "/"));
            ObjectNode capture = read(output);
            Instant.parse(capture.path("startedAt").asText());
            equal("interleaved.jsonl", capture.path("dataset").asText(), "dataset name");
            equal(hash(data.getBytes(StandardCharsets.UTF_8)), capture.path("datasetSha256").asText(), "dataset source hash");
            equal("stub-0.35", capture.at("/ollama/version").asText(), "Ollama provenance");
            equal("sha256:fixture", capture.at("/models/0/digest").asText(), "model provenance");
            sameJson(node(Map.of("allowThreshold", .9, "denyThreshold", .1, "minimumIntentMargin", .2,
                    "automaticRefundLimitCents", 10000)), capture.get("policy"), "policy metadata");
            sameJson(node(SPECS), capture.get("specs"), "all frozen specs embedded");
            equal(new Request("/api/version", "GET", null), server.requests.get(0), "version request");
            equal(new Request("/api/tags", "GET", null), server.requests.get(1), "tags request");
            List<Request> scored = server.scored();
            equal(15, scored.size(), "three warmups plus twelve interleaved responses");
            List<String> expectedNames = List.of("baseline", "split-v1", "split-v2", "baseline", "split-v1", "split-v2",
                    "baseline", "split-v1", "split-v2", "split-v2", "split-v1", "baseline", "split-v2", "split-v1", "baseline");
            for (int i = 0; i < scored.size(); i++) {
                String tool = (i >= 6 && i < 9) || i >= 12 ? "getOrder" : "issueRefund";
                JsonNode spec = SPECS.get(expectedNames.get(i));
                Request request = scored.get(i);
                equal("POST", request.method(), "SystemOne method");
                equal("tev1:4b", request.body().path("model").asText(), "model name");
                sameJson(spec.path("questions").path(tool), request.body().get("questions"), "question order " + i);
                sameJson(CompareQuestions.stateFor(tool.equals("getOrder") ? lookup() : refund(), spec),
                        request.body().get("state"), "state order " + i);
            }
            List<String> runOrder = List.of("baseline", "split-v1", "split-v2", "split-v2", "split-v1", "baseline");
            equal(6, capture.path("runs").size(), "run count");
            for (int i = 0; i < 6; i++) {
                JsonNode run = capture.path("runs").get(i);
                equal(runOrder.get(i), run.path("variant").asText(), "recorded variant order");
                equal(i / 3 + 1, run.path("repeat").asInt(), "recorded repeat");
                equal(3, run.path("rows").size(), "warmup is excluded");
                sameJson(run.get("summary"), CompareQuestions.summarize((ArrayNode) run.get("rows")), "generated summary");
                equal("preserved", run.at("/rows/0/customLabel").asText(), "custom case label");
                for (int j = 0; j < 3; j++) {
                    JsonNode row = run.path("rows").get(j);
                    JsonNode original = List.of(first, invalid, second).get(j);
                    original.properties().forEach(field -> sameJson(field.getValue(), row.get(field.getKey()), "case label"));
                    check(row.path("wallMillis").asDouble() >= 0, "elapsed time");
                }
            }
        }
    }

    void invalidModelCapture() throws Exception {
        Path directory = Files.createTempDirectory("question-invalid-model-");
        Path dataset = writeDataset(directory);
        Path output = directory.resolve("capture.json");
        try (FakeServer server = new FakeServer("invalid-model")) {
            success(cli("--dataset", dataset.toString(), "--variants", "split-v2", "--output", output.toString(),
                    "--ollama-url", server.url()));
            JsonNode row = read(output).at("/runs/0/rows/0");
            equal("REVIEW_REQUIRED", row.path("outcome").asText(), "invalid response outcome");
            equal("MODEL_UNAVAILABLE_OR_INVALID", row.at("/assessment/reason").asText(), "invalid response reason");
            sameJson(object().set("answers", object()), row.get("raw"), "invalid raw response retained");
        }
    }

    void invalidInputs() throws Exception {
        check(cli().status() != 0, "output required");
        Path directory = Files.createTempDirectory("question-invalid-input-");
        Path output = directory.resolve("capture.json");
        Path dataset = writeDataset(directory);
        for (String[] option : List.of(new String[] {"--unknown"}, new String[] {"--repeats", "0"},
                new String[] {"--repeats", "-1"}, new String[] {"--repeats", "1.5"},
                new String[] {"--variants", "unknown"}, new String[] {"--variants"})) {
            Files.writeString(output, "sentinel");
            List<String> args = new ArrayList<>(List.of("--dataset", dataset.toString(), "--output", output.toString(),
                    "--ollama-url", "http://127.0.0.1:1"));
            args.addAll(List.of(option));
            failurePreservesOutput(cli(args.toArray(String[]::new)), output);
        }
        for (String content : List.of("", "{broken", "[]", "{}", "null", "{\"id\":\"missing-labels\",\"request\":{}}")) {
            Files.writeString(dataset, content);
            Files.writeString(output, "sentinel");
            failurePreservesOutput(cli("--dataset", dataset.toString(), "--output", output.toString(),
                    "--ollama-url", "http://127.0.0.1:1"), output);
        }
        Files.writeString(output, "sentinel");
        failurePreservesOutput(cli("--dataset", directory.resolve("missing.jsonl").toString(), "--output", output.toString()), output);
    }

    void httpFailures() throws Exception {
        Path directory = Files.createTempDirectory("question-http-error-");
        Path dataset = writeDataset(directory);
        Path output = directory.resolve("capture.json");
        for (String mode : List.of("warmup-error", "score-error", "score-json", "score-trailing-json", "tags-missing", "version-json")) {
            Files.writeString(output, "sentinel");
            try (FakeServer server = new FakeServer(mode)) {
                failurePreservesOutput(cli("--dataset", dataset.toString(), "--variants", "split-v2", "--output", output.toString(),
                        "--ollama-url", server.url()), output);
            }
        }
    }

    void productionEntryPoint() throws Exception {
        Path directory = Files.createTempDirectory("question-entry-point-");
        Path output = directory.resolve("capture.json");
        Path stderr = directory.resolve("stderr.txt");
        try (FakeServer server = new FakeServer("normal")) {
            Process process = new ProcessBuilder("jbang", root.resolve("scripts/CompareQuestions.java").toString(),
                    "--output", output.toString(), "--ollama-url", server.url())
                    .directory(directory.toFile()).redirectOutput(directory.resolve("stdout.txt").toFile())
                    .redirectError(stderr.toFile()).start();
            if (!process.waitFor(45, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("Production JBang command timed out");
            }
            equal(0, process.exitValue(), "Production JBang command: " + Files.readString(stderr));
            ObjectNode capture = read(output);
            equal(2, capture.path("runs").size(), "default variant count");
            equal("baseline", capture.at("/runs/0/variant").asText(), "first default variant");
            equal("split-v2", capture.at("/runs/1/variant").asText(), "second default variant");
            equal(30, capture.at("/runs/0/rows").size(), "default cases");
            equal(30, capture.at("/runs/1/rows").size(), "default cases repeated per variant");
            equal(52, server.requests.size(), "two provenance, two warmups and 48 scored proposals");
            equal(hash(Files.readAllBytes(root.resolve("evaluation/cases.jsonl"))), capture.path("datasetSha256").asText(),
                    "production bundled dataset hash");
        }
    }

    private static Path writeDataset(Path directory) throws IOException {
        Path dataset = directory.resolve("cases.jsonl");
        Files.writeString(dataset, testCase("valid", refund(), true, "REFUND", "ALLOW") + "\n");
        return dataset;
    }

    private static JsonNode spec() {
        return SPECS.get("split-v2");
    }

    private static ObjectNode refund() {
        ObjectNode request = object().put("userRequest", "Return EUR 19.99 for order 4712 now.").put("toolName", "issueRefund");
        request.putObject("arguments").put("orderId", "4712").put("amountCents", 1999);
        return request;
    }

    private static ObjectNode lookup() {
        ObjectNode request = object().put("userRequest", "Read the details of order 4712.").put("toolName", "getOrder");
        request.putObject("arguments").put("orderId", "4712");
        return request;
    }

    private static ObjectNode response(String tool, JsonNode spec) {
        ObjectNode response = object().put("model", "tev1:test");
        ObjectNode answers = response.putObject("answers");
        String choice = tool.equals("issueRefund") ? "REFUND" : "ORDER_LOOKUP";
        ObjectNode intent = answers.putObject("intent").put("type", "choice").put("choice", choice).put("confidence", .91);
        intent.putObject("probabilities").put("REFUND", choice.equals("REFUND") ? .98 : .01)
                .put("ORDER_LOOKUP", choice.equals("ORDER_LOOKUP") ? .98 : .01).put("OTHER", .01);
        for (JsonNode name : spec.path("requiredChecks").path(tool)) {
            answers.putObject(name.asText()).put("type", "noul").put("noul", .97);
        }
        response.putObject("usage").put("input_tokens", 300).put("output_tokens", 4);
        return response;
    }

    private static ObjectNode testCase(String id, JsonNode request, boolean eligible, String intent, String outcome) {
        ObjectNode row = object().put("id", id).put("allowEligible", eligible).put("expectedIntent", intent);
        row.putArray("expectedOutcomes").add(outcome);
        row.set("request", request);
        return row;
    }

    private static void invalidResponse(JsonNode response, JsonNode request) {
        ObjectNode result = CompareQuestions.classify(request, response, spec());
        equal("REVIEW_REQUIRED", result.path("outcome").asText(), "invalid model response");
        equal("MODEL_UNAVAILABLE_OR_INVALID", result.path("reason").asText(), "invalid model reason");
        check(result.path("intent").isNull(), "invalid intent is null");
        check(result.path("minimumCheckScore").isNull(), "invalid minimum score is null");
        check(result.path("intentMargin").isNull(), "invalid margin is null");
        equal(0, result.path("checks").size(), "invalid checks empty");
    }

    private static Result cli(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine command = CompareQuestions.commandLine().setOut(new PrintWriter(out, true)).setErr(new PrintWriter(err, true));
        int status = command.execute(args);
        return new Result(status, out.toString(), err.toString());
    }

    private static ObjectNode object() {
        return CompareQuestions.JSON.createObjectNode();
    }

    private static ArrayNode array() {
        return CompareQuestions.JSON.createArrayNode();
    }

    private static JsonNode node(Object value) {
        return CompareQuestions.JSON.valueToTree(value);
    }

    private static ObjectNode replace(ObjectNode object, String field, Object value) {
        object.set(field, node(value));
        return object;
    }

    private static ObjectNode read(Path path) throws IOException {
        return (ObjectNode) CompareQuestions.JSON.readTree(path.toFile());
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void success(Result result) {
        check(result.status() == 0, "CLI returned " + result.status() + ": " + result.err());
    }

    private static void failurePreservesOutput(Result result, Path output) throws IOException {
        check(result.status() != 0, "Expected CLI failure: " + result.out());
        check(!result.err().isBlank(), "Failure should explain the error");
        equal("sentinel", Files.readString(output), "existing output preserved");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (expected instanceof Number left && actual instanceof Number right) {
            check(Math.abs(left.doubleValue() - right.doubleValue()) <= 1e-9, message + ": expected " + expected + ", got " + actual);
        } else {
            check(expected.equals(actual), message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void sameJson(JsonNode expected, JsonNode actual, String path) {
        check(expected != null && actual != null, path + ": missing JSON value");
        if (expected.isNumber() && actual.isNumber()) {
            equal(expected.doubleValue(), actual.doubleValue(), path);
        } else if (expected.isObject() && actual.isObject()) {
            equal(expected.size(), actual.size(), path + " field count");
            expected.properties().forEach(entry -> sameJson(entry.getValue(), actual.get(entry.getKey()), path + "/" + entry.getKey()));
        } else if (expected.isArray() && actual.isArray()) {
            equal(expected.size(), actual.size(), path + " array length");
            for (int i = 0; i < expected.size(); i++) {
                sameJson(expected.get(i), actual.get(i), path + "/" + i);
            }
        } else {
            check(expected.equals(actual), path + ": expected " + expected + ", got " + actual);
        }
    }

    static final class FakeServer implements AutoCloseable {
        final List<Request> requests = new CopyOnWriteArrayList<>();
        private final HttpServer server;
        private final String mode;
        private int modelCalls;

        FakeServer(String mode) throws IOException {
            this.mode = mode;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        List<Request> scored() {
            return requests.stream().filter(request -> request.path().equals("/v1/systemone")).toList();
        }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] input = exchange.getRequestBody().readAllBytes();
            JsonNode body = input.length == 0 ? null : CompareQuestions.JSON.readTree(input);
            String path = exchange.getRequestURI().getPath();
            requests.add(new Request(path, exchange.getRequestMethod(), body));
            String text;
            int status = 200;
            if (path.equals("/api/version")) {
                text = mode.equals("version-json") ? "{broken" : "{\"version\":\"stub-0.35\"}";
            } else if (path.equals("/api/tags")) {
                text = mode.equals("tags-missing") ? "{}"
                        : "{\"models\":[{\"name\":\"tev1:test\",\"digest\":\"sha256:fixture\"}]}";
            } else if (path.equals("/v1/systemone")) {
                modelCalls++;
                if ((mode.equals("warmup-error") && modelCalls == 1) || (mode.equals("score-error") && modelCalls > 1)) {
                    status = 503;
                    text = "{\"error\":\"unavailable\"}";
                } else if (mode.equals("score-json") && modelCalls > 1) {
                    text = "{broken";
                } else if (mode.equals("score-trailing-json") && modelCalls > 1) {
                    text = "{} {}";
                } else if (mode.equals("invalid-model")) {
                    text = "{\"answers\":{}}";
                } else {
                    String tool = body.at("/state/arguments").has("amountCents") ? "issueRefund" : "getOrder";
                    JsonNode matched = SPECS.values().stream()
                            .filter(spec -> spec.path("questions").path(tool).equals(body.path("questions")))
                            .findFirst().orElse(null);
                    if (matched == null) {
                        status = 400;
                        text = "{\"error\":\"unknown question payload\"}";
                    } else {
                        text = response(tool, matched).toString();
                    }
                }
            } else {
                status = 404;
                text = "{}";
            }
            try (exchange) {
                byte[] output = text.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, output.length);
                exchange.getResponseBody().write(output);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
