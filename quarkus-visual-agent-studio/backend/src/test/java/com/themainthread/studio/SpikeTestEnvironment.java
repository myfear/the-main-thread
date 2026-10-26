package com.themainthread.studio;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class SpikeTestEnvironment implements QuarkusTestResourceLifecycleManager {
    private Path directory;

    @Override
    public Map<String, String> start() {
        try (var socket = new ServerSocket(0)) {
            directory = Files.createTempDirectory("agent-studio-workflows-");
            int port = socket.getLocalPort();
            return Map.of("quarkus.flow.runner.source.path", directory.toString(),
                    "quarkus.http.test-port", Integer.toString(port),
                    "studio.executor-base-url", "http://127.0.0.1:" + port);
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare the isolated workflow test directory.", e);
        }
    }

    @Override
    public void stop() {
        if (directory == null) {
            return;
        }
        try (var files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not remove the isolated workflow test directory.", e);
        }
    }
}
