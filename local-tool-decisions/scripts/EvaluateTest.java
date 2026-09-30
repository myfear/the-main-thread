//JAVA 21+
//SOURCES Evaluate.java
//FILES default-cases.jsonl=../evaluation/cases.jsonl
//DEPS com.fasterxml.jackson.core:jackson-databind:2.22.2
//DEPS info.picocli:picocli:4.7.7

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
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

/** Standalone offline compatibility tests; the HTTP server below never calls a model. */
public class EvaluateTest {
    private static Path root;
    private static final Evaluate.Policy DEFAULT_POLICY = new Evaluate.Policy(.1, .2, 10000);

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
        var suite = new EvaluateTest();
        Map<String, Check> checks = new LinkedHashMap<>();
        checks.put("saved summaries match both Python captures", suite::savedSummaryParity);
        checks.put("reuse preserves provenance and inherits policy without servers", suite::reusePreservesCapture);
        checks.put("reuse overrides win and defaults fill missing policy", suite::policyOverridesAndDefaults);
        checks.put("threshold and refund limit boundaries match", suite::decisionBoundaries);
        checks.put("scoreless outcomes survive threshold sweeps", suite::scorelessOutcomes);
        checks.put("summary medians percentiles and null precision match", suite::summaryStatistics);
        checks.put("default dataset resource matches original source bytes", suite::defaultDataset);
        checks.put("fake HTTP capture preserves payloads warmup labels and provenance", suite::liveCapture);
        checks.put("default live dataset works outside the module", suite::liveDefaultDataset);
        checks.put("production JBang entry point works from a temporary directory", suite::productionEntryPoint);
        checks.put("required flags and invalid policy fail without overwriting output", suite::invalidOptions);
        checks.put("invalid and missing input files fail without overwriting output", suite::invalidFiles);
        checks.put("HTTP failures and malformed JSON fail without overwriting output", suite::httpFailures);
        ArrayNode results = Evaluate.JSON.createArrayNode();
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
        ObjectNode report = Evaluate.JSON.createObjectNode();
        report.put("tests", checks.size()).put("passed", checks.size() - failures).put("failed", failures)
                .put("liveModelCalls", 0).put("projectRoot", root.toString());
        report.set("results", results);
        // This is the runner's machine-readable result, not application logging.
        Evaluate.JSON.writerWithDefaultPrettyPrinter().writeValue(System.out, report);
        if (failures > 0) {
            System.exit(1);
        }
    }

    void savedSummaryParity() throws Exception {
        for (String file : List.of("tev1-4b-2026-09-30.json", "tev1-4b-with-extension-2026-09-30.json")) {
            ObjectNode capture = read(root.resolve("evaluation").resolve(file));
            sameJson(capture.get("summary"), Evaluate.summarize((ArrayNode) capture.get("rows"), DEFAULT_POLICY), file);
        }
    }

    void reusePreservesCapture() throws Exception {
        Path directory = Files.createTempDirectory("evaluate-reuse-");
        ObjectNode capture = read(root.resolve("evaluation/tev1-4b-with-extension-2026-09-30.json"));
        capture.put("customProvenance", "retained");
        Path input = directory.resolve("capture.json");
        Path output = directory.resolve("nested/result.json");
        Evaluate.JSON.writeValue(input.toFile(), capture);
        Result result = cli("--reuse", input.toString(), "--output", output.toString(),
                "--base-url", "http://127.0.0.1:1", "--ollama-url", "http://127.0.0.1:1",
                "--dataset", directory.resolve("does-not-exist.jsonl").toString());
        success(result);
        ObjectNode actual = read(output);
        ObjectNode expected = capture.deepCopy();
        expected.set("sweepPolicy", capture.get("policy").deepCopy());
        sameJson(expected, actual, "reused capture");
        sameJson(capture, read(input), "input capture must remain unchanged");
    }

    void policyOverridesAndDefaults() throws Exception {
        Path directory = Files.createTempDirectory("evaluate-policy-");
        ObjectNode capture = read(root.resolve("evaluation/tev1-4b-with-extension-2026-09-30.json"));
        ObjectNode originalPolicy = capture.withObject("policy");
        originalPolicy.put("denyThreshold", .05).put("minimumIntentMargin", .15)
                .put("automaticRefundLimitCents", 6000);
        Path input = directory.resolve("input.json");
        Path output = directory.resolve("output.json");
        Evaluate.JSON.writeValue(input.toFile(), capture);
        success(cli("--reuse", input.toString(), "--output", output.toString()));
        sameJson(originalPolicy, read(output).get("sweepPolicy"), "recorded policy inheritance");

        success(cli("--reuse", input.toString(), "--output", output.toString(), "--deny-threshold", ".3",
                "--minimum-margin", ".75", "--automatic-limit", "500"));
        ObjectNode changed = read(output);
        equal(.3, changed.at("/sweepPolicy/denyThreshold").asDouble(), "deny override");
        equal(.75, changed.at("/sweepPolicy/minimumIntentMargin").asDouble(), "margin override");
        equal(500, changed.at("/sweepPolicy/automaticRefundLimitCents").asLong(), "limit override");
        sameJson(originalPolicy, changed.get("policy"), "original policy retained");
        sameJson(capture.get("rows"), changed.get("rows"), "raw responses retained");
        sameJson(Evaluate.summarize((ArrayNode) capture.get("rows"), new Evaluate.Policy(.3, .75, 500)),
                changed.get("summary"), "override summary");
        check(!capture.get("summary").equals(changed.get("summary")), "overrides must change the sweep");

        capture.remove("policy");
        Evaluate.JSON.writeValue(input.toFile(), capture);
        success(cli("--reuse", input.toString(), "--output", output.toString()));
        sameJson(Evaluate.JSON.readTree("{\"denyThreshold\":0.1,\"minimumIntentMargin\":0.2,\"automaticRefundLimitCents\":10000}"),
                read(output).get("sweepPolicy"), "missing policy defaults");
    }

    void decisionBoundaries() throws Exception {
        ObjectNode row = row("refund", true, "issueRefund", 1999, "REFUND", .99, .8, "ALLOW");
        for (double alignment : new double[] {.1, .10001, .89999, .9, 1}) {
            row.withObject("response").withObject("scores").put("alignment", alignment);
            String expected = alignment <= .1 ? "DENY" : alignment < .9 ? "REVIEW_REQUIRED" : "ALLOW";
            equal(expected, Evaluate.decide(row, .9, DEFAULT_POLICY), "alignment " + alignment);
        }
        row.withObject("response").withObject("scores").put("alignment", .99).put("intentMargin", .19999);
        equal("REVIEW_REQUIRED", Evaluate.decide(row, .9, DEFAULT_POLICY), "small intent margin");
        row.withObject("response").withObject("scores").put("intentMargin", .2);
        equal("ALLOW", Evaluate.decide(row, .9, DEFAULT_POLICY), "inclusive margin");
        row.withObject("response").withObject("scores").put("intent", "ORDER_LOOKUP");
        equal("REVIEW_REQUIRED", Evaluate.decide(row, .9, DEFAULT_POLICY), "conflicting intent");
        row.withObject("response").withObject("scores").put("intent", "REFUND");
        row.withObject("request").withObject("arguments").put("orderId", "4711").put("amountCents", 10000);
        equal("ALLOW", Evaluate.decide(row, .9, DEFAULT_POLICY), "inclusive automatic cap");
        row.withObject("request").withObject("arguments").put("amountCents", 10001);
        equal("REVIEW_REQUIRED", Evaluate.decide(row, .9, DEFAULT_POLICY), "above automatic cap");
        ObjectNode read = row("lookup", true, "getOrder", 0, "ORDER_LOOKUP", .99, .8, "ALLOW");
        equal("ALLOW", Evaluate.decide(read, .9, DEFAULT_POLICY), "read branch expects ORDER_LOOKUP");
    }

    void scorelessOutcomes() throws Exception {
        for (String outcome : List.of("DENY", "REVIEW_REQUIRED")) {
            ObjectNode row = row("scoreless", false, "issueRefund", 1000, "REFUND", .9, .9, outcome);
            row.withObject("response").putNull("scores");
            for (double threshold : new double[] {.5, .75, .9, .95, .99}) {
                equal(outcome, Evaluate.decide(row, threshold, DEFAULT_POLICY), "scoreless outcome");
            }
        }
    }

    void summaryStatistics() throws Exception {
        ArrayNode rows = Evaluate.JSON.createArrayNode();
        for (int i = 1; i <= 20; i++) {
            ObjectNode row = row("invalid-" + i, false, "issueRefund", 1000, "REFUND", 0, 0, "DENY");
            row.withObject("response").putNull("scores");
            row.put("wallMillis", i);
            rows.add(row);
        }
        ObjectNode summary = Evaluate.summarize(rows, DEFAULT_POLICY);
        equal(10.5, summary.at("/wallMillis/median").asDouble(), "even median");
        equal(19, summary.at("/wallMillis/p95").asDouble(), "nearest-rank p95");
        check(summary.get("modelPathWallMillis").isNull(), "scoreless model latency is null");
        check(summary.at("/thresholdSweep/0/allowPrecision").isNull(), "no allowed calls means null precision");
        equal(20, summary.at("/thresholdSweep/0/denyCount").asInt(), "deny count");
        rows.remove(19);
        equal(10, Evaluate.summarize(rows, DEFAULT_POLICY).at("/wallMillis/median").asDouble(), "odd median");
    }

    void defaultDataset() throws Exception {
        Path expected = root.resolve("evaluation/cases.jsonl").toRealPath();
        check(java.util.Arrays.equals(Files.readAllBytes(expected), Evaluate.defaultDataset()), "bundled dataset bytes match source");
        equal(30, Files.readAllLines(expected).stream().filter(line -> !line.isBlank()).count(), "default case count");
    }

    void liveCapture() throws Exception {
        Path directory = Files.createTempDirectory("evaluate-live-");
        Path dataset = directory.resolve("cases.jsonl");
        Path output = directory.resolve("nested/capture.json");
        ObjectNode first = row("first", true, "issueRefund", 1999, "REFUND", .97, .97, "ALLOW");
        ObjectNode second = row("second", false, "issueRefund", -10, "REFUND", .97, .97, "DENY");
        first.remove(List.of("response", "wallMillis"));
        second.remove(List.of("response", "wallMillis"));
        first.put("customLabel", "keep this field");
        String data = first + "\n\n" + second + "\n";
        Files.writeString(dataset, data);
        try (FakeServer server = new FakeServer("normal")) {
            success(cli("--dataset", dataset.toString(), "--output", output.toString(),
                    "--base-url", server.url(), "--ollama-url", server.url(), "--automatic-limit", "8000"));
            ObjectNode capture = read(output);
            Instant.parse(capture.get("capturedAt").asText());
            equal(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8))),
                    capture.get("datasetSha256").asText(), "dataset hash includes blank lines and newline");
            equal("stub-0.35", capture.at("/ollama/version").asText(), "Ollama provenance");
            equal("sha256:fixture", capture.at("/models/0/digest").asText(), "model provenance");
            equal(8000, capture.at("/policy/automaticRefundLimitCents").asLong(), "policy metadata");
            equal(5, server.requests.size(), "two provenance requests, warmup, two reviews");
            equal(new Request("/api/version", "GET", null), server.requests.get(0), "version request");
            equal(new Request("/api/tags", "GET", null), server.requests.get(1), "tags request");
            for (int i = 2; i < server.requests.size(); i++) {
                equal("/reviews", server.requests.get(i).path(), "read-only review endpoint");
                equal("POST", server.requests.get(i).method(), "review method");
                sameJson(i == 4 ? second.get("request") : first.get("request"), server.requests.get(i).body(), "forwarded payload");
            }
            sameJson(capture.at("/warmup/response"), capture.at("/rows/0/response"), "warmup response retained separately");
            check(capture.at("/warmup/wallMillis").asDouble() >= 0, "warmup elapsed time");
            equal(2, capture.withArray("rows").size(), "warmup is not an evaluated row");
            for (int i = 0; i < 2; i++) {
                ObjectNode expected = i == 0 ? first : second;
                JsonNode actual = capture.withArray("rows").get(i);
                for (var field : expected.properties()) {
                    sameJson(field.getValue(), actual.get(field.getKey()), "case field " + field.getKey());
                }
                check(actual.get("wallMillis").asDouble() >= 0, "case elapsed time");
            }
            equal("DENY", capture.at("/rows/1/response/outcome").asText(), "scoreless review preserved");
        }
    }

    void liveDefaultDataset() throws Exception {
        Path output = Files.createTempDirectory("evaluate-default-").resolve("capture.json");
        try (FakeServer server = new FakeServer("normal")) {
            success(cli("--output", output.toString(), "--base-url", server.url(), "--ollama-url", server.url()));
            equal(33, server.requests.size(), "30 default cases plus warmup and provenance");
            equal(30, read(output).withArray("rows").size(), "default dataset loaded");
        }
    }

    void productionEntryPoint() throws Exception {
        Path directory = Files.createTempDirectory("evaluate-entry-point-");
        Path output = directory.resolve("capture.json");
        Path stderr = directory.resolve("stderr.txt");
        try (FakeServer server = new FakeServer("normal")) {
            Process process = new ProcessBuilder("jbang", root.resolve("scripts/Evaluate.java").toString(),
                    "--output", output.toString(), "--base-url", server.url(), "--ollama-url", server.url())
                    .directory(directory.toFile())
                    .redirectOutput(directory.resolve("stdout.json").toFile())
                    .redirectError(stderr.toFile())
                    .start();
            if (!process.waitFor(45, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("Production JBang entry point timed out");
            }
            check(process.exitValue() == 0, "Production JBang command failed: " + Files.readString(stderr));
            ObjectNode capture = read(output);
            equal(30, capture.withArray("rows").size(), "production default cases");
            equal(33, server.requests.size(), "production HTTP call count");
            byte[] expectedHash = MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(root.resolve("evaluation/cases.jsonl")));
            equal(HexFormat.of().formatHex(expectedHash),
                    capture.get("datasetSha256").asText(), "production bundled dataset hash");
        }
    }

    void invalidOptions() throws Exception {
        check(cli().status() != 0, "output is required");
        Path output = Files.createTempDirectory("evaluate-options-").resolve("output.json");
        for (String[] option : List.of(new String[] {"--unknown"}, new String[] {"--deny-threshold", "-0.1"},
                new String[] {"--deny-threshold", ".5"}, new String[] {"--deny-threshold", "NaN"},
                new String[] {"--minimum-margin", "1.01"}, new String[] {"--minimum-margin", "-0.1"},
                new String[] {"--minimum-margin", "NaN"}, new String[] {"--automatic-limit", "0"},
                new String[] {"--automatic-limit", "1.5"})) {
            Files.writeString(output, "sentinel");
            List<String> args = new ArrayList<>(List.of("--output", output.toString(), "--reuse",
                    root.resolve("evaluation/tev1-4b-with-extension-2026-09-30.json").toString()));
            args.addAll(List.of(option));
            failurePreservesOutput(cli(args.toArray(String[]::new)), output);
        }
    }

    void invalidFiles() throws Exception {
        Path directory = Files.createTempDirectory("evaluate-files-");
        Path output = directory.resolve("output.json");
        Path input = directory.resolve("input.json");
        for (String mode : List.of("--reuse", "--dataset")) {
            for (String content : List.of("{broken", "", "[]", "{\"rows\":[]}")) {
                Files.writeString(input, content);
                Files.writeString(output, "sentinel");
                failurePreservesOutput(cli(mode, input.toString(), "--output", output.toString(),
                        "--base-url", "http://127.0.0.1:1", "--ollama-url", "http://127.0.0.1:1"), output);
            }
            Files.writeString(output, "sentinel");
            failurePreservesOutput(cli(mode, directory.resolve("missing.json").toString(), "--output", output.toString()), output);
        }
    }

    void httpFailures() throws Exception {
        Path directory = Files.createTempDirectory("evaluate-http-errors-");
        Path dataset = directory.resolve("cases.jsonl");
        ObjectNode testCase = row("one", true, "issueRefund", 1999, "REFUND", .97, .97, "ALLOW");
        testCase.remove(List.of("response", "wallMillis"));
        Files.writeString(dataset, testCase + "\n");
        Path output = directory.resolve("output.json");
        for (String mode : List.of("review-error", "review-json", "tags-missing")) {
            Files.writeString(output, "sentinel");
            try (FakeServer server = new FakeServer(mode)) {
                failurePreservesOutput(cli("--dataset", dataset.toString(), "--output", output.toString(),
                        "--base-url", server.url(), "--ollama-url", server.url()), output);
            }
        }
    }

    private static Result cli(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine command = Evaluate.commandLine().setOut(new PrintWriter(out, true)).setErr(new PrintWriter(err, true));
        int status = command.execute(args);
        return new Result(status, out.toString(), err.toString());
    }

    private static ObjectNode row(String id, boolean eligible, String tool, long amount, String intent,
            double alignment, double margin, String outcome) throws Exception {
        ObjectNode row = Evaluate.JSON.createObjectNode().put("id", id).put("expectedIntent", intent).put("allowEligible", eligible);
        row.putArray("expectedOutcomes").add(outcome);
        ObjectNode request = row.putObject("request").put("userRequest", "Fixture request for " + id).put("toolName", tool);
        ObjectNode arguments = request.putObject("arguments").put("orderId", "4712");
        if (tool.equals("issueRefund")) {
            arguments.put("amountCents", amount);
        }
        ObjectNode response = row.putObject("response").put("outcome", outcome).put("reason", "FIXTURE")
                .put("toolName", tool).put("modelName", "tev1:test").put("latencyMillis", 1);
        ObjectNode scores = response.putObject("scores").put("intent", intent).put("alignment", alignment).put("intentMargin", margin);
        scores.putObject("intentProbabilities").put("REFUND", intent.equals("REFUND") ? .98 : .01)
                .put("ORDER_LOOKUP", intent.equals("ORDER_LOOKUP") ? .98 : .01).put("OTHER", .01);
        row.put("wallMillis", 2.0);
        return row;
    }

    private static ObjectNode read(Path path) throws IOException {
        return (ObjectNode) Evaluate.JSON.readTree(path.toFile());
    }

    private static void success(Result result) {
        check(result.status() == 0, "CLI returned " + result.status() + ": " + result.err());
    }

    private static void failurePreservesOutput(Result result, Path output) throws IOException {
        check(result.status() != 0, "Expected CLI failure: " + result.out());
        check(!result.err().isBlank(), "Failure should explain the error");
        equal("sentinel", Files.readString(output), "existing output must survive a failure");
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

        FakeServer(String mode) throws IOException {
            this.mode = mode;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] input = exchange.getRequestBody().readAllBytes();
            JsonNode body = input.length == 0 ? null : Evaluate.JSON.readTree(input);
            String path = exchange.getRequestURI().getPath();
            requests.add(new Request(path, exchange.getRequestMethod(), body));
            String text;
            int status = 200;
            if (path.equals("/api/version")) {
                text = "{\"version\":\"stub-0.35\"}";
            } else if (path.equals("/api/tags")) {
                text = mode.equals("tags-missing") ? "{}"
                        : "{\"models\":[{\"name\":\"tev1:test\",\"digest\":\"sha256:fixture\"}]}";
            } else if (path.equals("/reviews")) {
                if (mode.equals("review-error")) {
                    status = 503;
                    text = "{\"error\":\"unavailable\"}";
                } else if (mode.equals("review-json")) {
                    text = "{broken";
                } else if (body.path("arguments").path("amountCents").asLong() < 0) {
                    text = "{\"outcome\":\"DENY\",\"reason\":\"Invalid amount\",\"scores\":null}";
                } else {
                    String intent = body.path("toolName").asText().equals("issueRefund") ? "REFUND" : "ORDER_LOOKUP";
                    try {
                        text = row("stub", true, body.path("toolName").asText(), 1999, intent, .97, .97, "ALLOW")
                                .get("response").toString();
                    } catch (Exception e) {
                        throw new IOException(e);
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
