import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class StartupBenchmark {

    private static final int HTTP_PORT = 18080;
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
    private static final URI READINESS_URI = URI.create("http://127.0.0.1:" + HTTP_PORT + "/q/health/ready");

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            usage();
        }

        Mode mode = Mode.parse(args[0]);
        int runs = args.length == 2 ? Integer.parseInt(args[1]) : 20;
        if (runs < 5) {
            throw new IllegalArgumentException("Use at least five measured runs");
        }

        Path applicationDirectory = Path.of("target", "quarkus-app").toAbsolutePath();
        verifyArtifact(mode, applicationDirectory);

        System.out.printf(Locale.ROOT, "Mode: %s, measured runs: %d%n", mode.cliName, runs);
        System.out.println("Priming the database and filesystem cache; this run is excluded.");
        measureOnce(mode, applicationDirectory);

        List<Double> measurements = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            double milliseconds = measureOnce(mode, applicationDirectory);
            measurements.add(milliseconds);
            System.out.printf(Locale.ROOT, "run %02d: %.1f ms%n", run, milliseconds);
        }

        Collections.sort(measurements);
        double median = percentile(measurements, 0.50);
        double p95 = percentile(measurements, 0.95);
        System.out.printf(Locale.ROOT, "median: %.1f ms%n", median);
        System.out.printf(Locale.ROOT, "p95: %.1f ms%n", p95);
    }

    private static double measureOnce(Mode mode, Path applicationDirectory) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (mode == Mode.AOT) {
            command.add("-XX:AOTMode=on");
            command.add("-XX:AOTCache=app.aot");
            command.add("-Xlog:aot");
        }
        command.add("-Dquarkus.http.port=" + HTTP_PORT);
        command.add("-Dquarkus.log.console.level=WARN");
        command.add("-jar");
        command.add("quarkus-run.jar");

        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(applicationDirectory.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().putIfAbsent(
                "QUARKUS_DATASOURCE_JDBC_URL", "jdbc:postgresql://localhost:5432/swiftship");
        processBuilder.environment().putIfAbsent("QUARKUS_DATASOURCE_USERNAME", "swiftship");
        processBuilder.environment().putIfAbsent("QUARKUS_DATASOURCE_PASSWORD", "swiftship");

        long startedAt = System.nanoTime();
        Process process = processBuilder.start();
        StringBuffer output = new StringBuffer();
        Thread outputReader = Thread.ofVirtual().start(() -> readOutput(process, output));

        try {
            awaitReadiness(process, output);
            return (System.nanoTime() - startedAt) / 1_000_000.0;
        } finally {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            outputReader.join(Duration.ofSeconds(1));
        }
    }

    private static void awaitReadiness(Process process, StringBuffer output) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(250))
                .build();
        HttpRequest request = HttpRequest.newBuilder(READINESS_URI)
                .timeout(Duration.ofMillis(500))
                .GET()
                .build();
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Application exited before readiness:\n" + output);
            }
            try {
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // The socket is not accepting requests yet.
            }
            Thread.sleep(10);
        }
        throw new IllegalStateException("Readiness timed out:\n" + output);
    }

    private static void readOutput(Process process, StringBuffer output) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < 32_000) {
                    output.append(line).append(System.lineSeparator());
                }
            }
        } catch (IOException e) {
            if (process.isAlive()) {
                output.append("Failed to read process output: ").append(e.getMessage());
            }
        }
    }

    private static void verifyArtifact(Mode mode, Path applicationDirectory) {
        Path runner = applicationDirectory.resolve("quarkus-run.jar");
        if (!Files.isRegularFile(runner)) {
            throw new IllegalStateException("Missing " + runner + "; build the application first");
        }
        if (mode == Mode.AOT && !Files.isRegularFile(applicationDirectory.resolve("app.aot"))) {
            throw new IllegalStateException("Missing app.aot; run the AOT build first");
        }
    }

    private static double percentile(List<Double> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private static void usage() {
        System.err.println("Usage: java scripts/StartupBenchmark.java <fast|aot> [runs]");
        System.exit(2);
    }

    private enum Mode {
        FAST("fast"),
        AOT("aot");

        private final String cliName;

        Mode(String cliName) {
            this.cliName = cliName;
        }

        private static Mode parse(String value) {
            for (Mode mode : values()) {
                if (mode.cliName.equals(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown mode: " + value);
        }
    }
}
