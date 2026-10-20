package com.themainthread.pglite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class PackagedRestartIT {
    @TempDir
    Path directory;

    @Test
    void persistsAcrossIndependentJvmProcessesAfterSqlErrors() throws Exception {
        Path data = directory.resolve("database");
        String previous;
        try (var app = start("first", data)) {
            previous = app.get().path("instanceId").asText();
            app.sql("CREATE TABLE restart_probe (id int PRIMARY KEY, value text)", 200);
            app.post("/transaction", Map.of("statements", List.of(
                    "INSERT INTO restart_probe VALUES (1, 'committed')")), 200);
            app.sql("BEGIN; INSERT INTO restart_probe VALUES (2, 'uncommitted')", 200);
            assertEquals("22012", app.sql("SELECT 1 / 0", 400).path("sqlState").asText());
            assertEquals("E", app.get().path("transactionStatus").asText());
        }
        assertTrue(Files.isRegularFile(data.resolve("CURRENT")));
        try (var app = start("second", data)) {
            String instance = app.get().path("instanceId").asText();
            assertNotEquals(previous, instance);
            var rows = app.sql("SELECT id, value FROM restart_probe ORDER BY id", 200)
                    .path("statements").get(0).path("rows");
            assertEquals(1, rows.size());
            assertEquals("committed", rows.get(0).get(1).asText());
            assertEquals("42601", app.sql("SELEC broken", 400).path("sqlState").asText());
            assertEquals("42", app.sql("SELECT 42", 200).path("statements").get(0).path("rows").get(0).get(0).asText());
            assertEquals(instance, app.get().path("instanceId").asText());
        }
    }

    private App start(String name, Path data) throws Exception {
        Path log = directory.resolve(name + ".log");
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path jar = Path.of("target/quarkus-app/quarkus-run.jar").toAbsolutePath();
        Process process = new ProcessBuilder(java.toString(), "-Xmx1g", "-Dpglite.data-directory=" + data,
                "-Dquarkus.http.host=127.0.0.1", "-Dquarkus.http.port=0", "-jar", jar.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        Pattern listening = Pattern.compile("Listening on: http://127\\.0\\.0\\.1:(\\d+)");
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        try {
            while (System.nanoTime() < deadline) {
                if (!process.isAlive()) {
                    throw new IOException("Packaged JVM exited: " + Files.readString(log));
                }
                var match = listening.matcher(Files.readString(log));
                if (match.find()) {
                    return new App(process, log, URI.create("http://127.0.0.1:" + match.group(1) + "/pglite"));
                }
                Thread.sleep(100);
            }
            throw new IOException("Packaged JVM did not start: " + Files.readString(log));
        } catch (Exception e) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            throw e;
        }
    }

    private static final class App implements AutoCloseable {
        private final Process process;
        private final Path log;
        private final URI base;
        private final ObjectMapper mapper = new ObjectMapper();
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        private App(Process process, Path log, URI base) {
            this.process = process;
            this.log = log;
            this.base = base;
        }

        JsonNode get() throws Exception {
            return request(HttpRequest.newBuilder(base).GET().timeout(Duration.ofSeconds(60)).build(), 200);
        }

        JsonNode sql(String sql, int status) throws Exception {
            return post("/sql", Map.of("sql", sql), status);
        }

        JsonNode post(String path, Object body, int status) throws Exception {
            var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            return request(request, status);
        }

        private JsonNode request(HttpRequest request, int status) throws Exception {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(status, response.statusCode(), response.body() + "\n" + Files.readString(log));
            return mapper.readTree(response.body());
        }

        @Override
        public void close() throws Exception {
            client.close();
            // SIGTERM runs Quarkus shutdown hooks on the Unix JVMs used by this prototype.
            process.destroy();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                throw new IOException("Packaged JVM did not shut down gracefully: " + Files.readString(log));
            }
        }
    }
}
