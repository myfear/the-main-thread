package com.themainthread.studio.graph;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

@ApplicationScoped
public class GraphPublisher {
    private final GraphCompiler compiler;
    private final GraphStore store;
    private final WorkflowApplication application;
    private final Path directory;

    public GraphPublisher(GraphCompiler compiler, GraphStore store, WorkflowApplication application,
            @ConfigProperty(name = "quarkus.flow.runner.source.path") String directory) {
        this.compiler = compiler;
        this.store = store;
        this.application = application;
        this.directory = Path.of(directory);
    }

    public synchronized GraphStore.Publication publish(StudioGraph graph, String version) throws Exception {
        var compiled = compiler.compile(graph, version);
        store.insert(compiled);
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            Path artifact = directory.resolve(compiled.workflowName() + "-" + version + ".yaml");
            if (Files.exists(artifact)) {
                throw new IllegalStateException("An immutable workflow artifact already exists.");
            }
            temporary = Files.createTempFile(directory, "graph-publication-", ".tmp");
            Files.writeString(temporary, compiled.yaml());
            Files.move(temporary, artifact, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            store.status(compiled.publicationId(), "FAILED_TO_DEPLOY");
            throw e;
        } finally {
            if (temporary != null) {
                Files.deleteIfExists(temporary);
            }
        }
        return new GraphStore.Publication("PUBLISHING", compiled);
    }

    public GraphStore.Publication publication(UUID id) throws Exception {
        var publication = store.get(id);
        var compiled = publication.compiled();
        if ("PUBLISHING".equals(publication.status()) && application.workflowDefinitions()
                .containsKey(new WorkflowDefinitionId("studio", compiled.workflowName(), compiled.version()))) {
            store.status(id, "READY");
            return new GraphStore.Publication("READY", compiled);
        }
        return publication;
    }

    public GraphCompiler.Compiled ready(UUID id) throws Exception {
        var publication = publication(id);
        if (!"READY".equals(publication.status())) {
            throw new WebApplicationException("This workflow is not registered and ready.", 409);
        }
        return publication.compiled();
    }
}
