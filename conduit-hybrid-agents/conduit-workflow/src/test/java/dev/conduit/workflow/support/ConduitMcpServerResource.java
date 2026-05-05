package dev.conduit.workflow.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Builds {@code conduit-mcp-server}, runs {@code quarkus-run.jar} on a free port, and points {@code conduit.mcp.server-url}
 * at it so {@code @QuarkusTest} exercises real Streamable HTTP MCP (no stub client).
 */
public class ConduitMcpServerResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOG = Logger.getLogger(ConduitMcpServerResource.class);

    private Process serverProcess;

    @Override
    public Map<String, String> start() {
        String ollamaBase = System.getProperty(
                "conduit.test.ollama-base-url",
                System.getenv().getOrDefault("CONDUIT_TEST_OLLAMA_BASE_URL", "http://localhost:11434"));
        if (!OllamaTestSupport.canReachBaseUrl(ollamaBase)) {
            throw new IllegalStateException(
                    "Workflow tests expect Ollama listening at "
                            + ollamaBase
                            + ". Start Ollama locally or set CONDUIT_TEST_OLLAMA_BASE_URL / "
                            + "-Dconduit.test.ollama-base-url before running conduit-workflow tests.");
        }

        Path workflowDir = Path.of(System.getProperty("user.dir"));
        Path mcpDir = workflowDir.resolveSibling("conduit-mcp-server");
        Path mvnw = mcpDir.resolve("mvnw");
        Path jar = mcpDir.resolve("target/quarkus-app/quarkus-run.jar");

        if (!Files.isExecutable(mvnw)) {
            throw new IllegalStateException(
                    "Expected executable " + mvnw + " (run workflow tests from conduit-workflow module directory)");
        }

        try {
            LOG.infof("Building MCP module at %s", mcpDir);
            ProcessBuilder build =
                    new ProcessBuilder(mvnw.toString(), "-q", "package", "-DskipTests");
            build.directory(mcpDir.toFile());
            build.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            build.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process buildProc = build.start();
            if (!buildProc.waitFor(10, TimeUnit.MINUTES)) {
                buildProc.destroyForcibly();
                throw new IllegalStateException("Timed out building conduit-mcp-server");
            }
            if (buildProc.exitValue() != 0) {
                throw new IllegalStateException(
                        "conduit-mcp-server package failed with exit code " + buildProc.exitValue());
            }
            if (!Files.isReadable(jar)) {
                throw new IllegalStateException("Missing Quarkus runnable jar after build: " + jar);
            }

            int port = freePort();
            ProcessBuilder run = new ProcessBuilder(
                    resolveJavaExecutable(),
                    "-Dquarkus.http.host=127.0.0.1",
                    "-Dquarkus.http.port=" + port,
                    "-jar",
                    jar.toString());
            run.directory(mcpDir.toFile());
            run.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            run.redirectError(ProcessBuilder.Redirect.INHERIT);

            LOG.infof("Starting conduit-mcp-server on port %d", port);
            serverProcess = run.start();
            waitForPortOpen("127.0.0.1", port, Duration.ofMinutes(2));
            Thread.sleep(1500);

            String url = "http://127.0.0.1:" + port + "/mcp";
            LOG.infov("MCP server listening at {0}", url);
            return Map.of("conduit.mcp.server-url", url);
        } catch (RuntimeException e) {
            stopQuietly();
            throw e;
        } catch (Exception e) {
            stopQuietly();
            throw new IllegalStateException("Failed to start conduit-mcp-server for tests", e);
        }
    }

    @Override
    public void stop() {
        stopQuietly();
    }

    private void stopQuietly() {
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            try {
                if (!serverProcess.waitFor(30, TimeUnit.SECONDS)) {
                    serverProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                serverProcess.destroyForcibly();
            }
        }
        serverProcess = null;
    }

    private static String resolveJavaExecutable() {
        String home = System.getProperty("java.home");
        if (home != null) {
            Path java = Path.of(home).resolve("bin").resolve("java");
            if (Files.isExecutable(java)) {
                return java.toString();
            }
        }
        return "java";
    }

    private static int freePort() throws IOException {
        try (Socket socket = new Socket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    private static void waitForPortOpen(String host, int port, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        IOException last = null;
        while (Instant.now().isBefore(deadline)) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 300);
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(200);
            }
        }
        throw new IllegalStateException(
                "Timed out waiting for MCP HTTP port " + host + ":" + port + " (" + last + ")");
    }
}
