package com.themainthread.studio.graph;

import java.sql.SQLException;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class GraphStore {
    private final AgroalDataSource dataSource;
    private final ObjectMapper mapper;

    public GraphStore(AgroalDataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    public void insert(GraphCompiler.Compiled compiled) throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        INSERT INTO studio.graph_publication (id, flow_id, version, status, compiled_json)
                        VALUES (?, ?, ?, 'PUBLISHING', ?::jsonb)
                        """)) {
            statement.setObject(1, compiled.publicationId());
            statement.setObject(2, compiled.flowId());
            statement.setString(3, compiled.version());
            statement.setString(4, mapper.writeValueAsString(compiled));
            statement.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new WebApplicationException("This flow version is immutable and already exists.", 409);
            }
            throw e;
        }
    }

    public Publication get(UUID id) throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT status, compiled_json FROM studio.graph_publication WHERE id = ?")) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new NotFoundException();
                }
                return new Publication(rows.getString(1),
                        mapper.readValue(rows.getString(2), GraphCompiler.Compiled.class));
            }
        }
    }

    public void status(UUID id, String status) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "UPDATE studio.graph_publication SET status = ? WHERE id = ?")) {
            statement.setString(1, status);
            statement.setObject(2, id);
            statement.executeUpdate();
        }
    }

    public UUID createRun(UUID publication) throws SQLException {
        UUID run = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        INSERT INTO studio.graph_run (id, publication_id, start_status) VALUES (?, ?, 'STARTING')
                        """)) {
            statement.setObject(1, run);
            statement.setObject(2, publication);
            statement.executeUpdate();
        }
        return run;
    }

    public Run run(UUID id) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT publication_id, engine_instance_id, start_status FROM studio.graph_run WHERE id = ?")) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new NotFoundException();
                }
                return new Run(id, rows.getObject(1, UUID.class), rows.getString(2), rows.getString(3));
            }
        }
    }

    public void started(UUID run, String instance, String status) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "UPDATE studio.graph_run SET engine_instance_id = ?, start_status = ? WHERE id = ?")) {
            statement.setString(1, instance);
            statement.setString(2, status);
            statement.setObject(3, run);
            statement.executeUpdate();
        }
    }

    public record Publication(String status, GraphCompiler.Compiled compiled) {
    }

    public record Run(UUID id, UUID publicationId, String instanceId, String startStatus) {
    }
}
