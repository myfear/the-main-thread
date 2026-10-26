package com.themainthread.studio;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.WorkflowWriter;

@ApplicationScoped
public class SpikePublisher {
    private final Path directory;
    private final StudioConfig config;

    public SpikePublisher(@ConfigProperty(name = "quarkus.flow.runner.source.path") String directory,
            StudioConfig config) {
        this.directory = Path.of(directory);
        this.config = config;
    }

    public synchronized Publication publish(String version) throws IOException {
        if (version == null || !version.matches("0\\.0\\.[1-9][0-9]{0,8}")) {
            throw new BadRequestException("Use a version between 0.0.1 and 0.0.999999999.");
        }
        URI executor = URI.create(config.executorBaseUrl());
        if (!"http".equals(executor.getScheme()) || !"127.0.0.1".equals(executor.getHost())
                || executor.getPort() < 1 || !executor.getPath().isEmpty()) {
            throw new IllegalStateException("The spike executor must use a fixed loopback HTTP origin.");
        }
        Files.createDirectories(directory);
        Path published = directory.resolve("risk-triage-" + version + ".yaml");
        if (Files.exists(published)) {
            throw new WebApplicationException("This workflow version already exists.", 409);
        }
        String template;
        try (var stream = getClass().getResourceAsStream("/studio/runtime-spike.yaml.template")) {
            if (stream == null) {
                throw new IllegalStateException("Missing runtime spike template.");
            }
            template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path temporary = Files.createTempFile(directory, "publishing-", ".tmp");
        try {
            Files.writeString(temporary, template.replace("@VERSION@", version)
                    .replace("@EXECUTOR_URL@", executor.toString()));
            var workflow = WorkflowReader.readWorkflow(temporary, WorkflowFormat.YAML, WorkflowReader.validation());
            WorkflowWriter.writeWorkflow(temporary, workflow, WorkflowFormat.YAML);
            Files.move(temporary, published, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new Publication("studio", "risk-triage", version, "PUBLISHING");
    }

    public record Publication(String namespace, String name, String version, String status) {
    }
}
