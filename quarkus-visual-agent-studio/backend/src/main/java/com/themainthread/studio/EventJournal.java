package com.themainthread.studio;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class EventJournal {
    private static final Logger LOG = Logger.getLogger(EventJournal.class);
    private final AgroalDataSource dataSource;
    private final ObjectMapper mapper;
    private final AtomicLong lost = new AtomicLong();
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(512), Thread.ofPlatform().name("studio-event-journal-", 0).factory());

    public EventJournal(AgroalDataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    public void append(PendingEvent event) {
        try {
            writer.execute(() -> persist(event));
        } catch (RejectedExecutionException e) {
            lost.incrementAndGet();
            LOG.error("Studio lifecycle event queue rejected an event.", e);
        }
    }

    private void persist(PendingEvent event) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        INSERT INTO studio.runtime_event
                        (engine_instance_id, run_id, workflow_namespace, workflow_name, workflow_version,
                         kind, task_identity, attempt, occurred_at, output_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        """)) {
            statement.setString(1, event.instanceId());
            statement.setObject(2, event.runId());
            statement.setString(3, event.namespace());
            statement.setString(4, event.name());
            statement.setString(5, event.version());
            statement.setString(6, event.kind());
            statement.setString(7, event.task());
            statement.setInt(8, event.attempt());
            statement.setObject(9, event.occurredAt());
            statement.setString(10, event.output() == null ? null : mapper.writeValueAsString(event.output()));
            statement.executeUpdate();
        } catch (Exception e) {
            lost.incrementAndGet();
            LOG.error("Studio lifecycle event could not be persisted.", e);
        }
    }

    public List<StoredEvent> events(String instanceId) throws Exception {
        return events("SELECT * FROM studio.runtime_event WHERE engine_instance_id = ? ORDER BY id", instanceId);
    }

    public List<StoredEvent> eventsForRun(UUID runId) throws Exception {
        return events("SELECT * FROM studio.runtime_event WHERE run_id = ? ORDER BY id", runId);
    }

    private List<StoredEvent> events(String query, Object identity) throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(query)) {
            statement.setObject(1, identity);
            var events = new ArrayList<StoredEvent>();
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    String output = rows.getString("output_json");
                    events.add(new StoredEvent(rows.getLong("id"), rows.getString("kind"),
                            rows.getString("task_identity"), rows.getString("run_id"),
                            rows.getString("workflow_namespace"), rows.getString("workflow_name"),
                            rows.getString("workflow_version"), rows.getInt("attempt"),
                            rows.getObject("occurred_at", OffsetDateTime.class),
                            output == null ? null : mapper.readTree(output)));
                }
            }
            return events;
        }
    }

    public long checkpoints(String instanceId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT count(*) FROM task_info_entity WHERE workflow_instance_id = ?")) {
            statement.setString(1, instanceId);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    public long lostEvents() {
        return lost.get();
    }

    @PreDestroy
    void stop() throws InterruptedException {
        writer.shutdown();
        if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
            LOG.warn("Studio event journal did not drain before shutdown.");
            writer.shutdownNow();
        }
    }

    public record PendingEvent(String instanceId, UUID runId, String namespace, String name, String version,
            String kind, String task, int attempt, OffsetDateTime occurredAt, Object output) {
    }

    public record StoredEvent(long eventId, String kind, String task, String runId, String namespace, String name,
            String version, int attempt, OffsetDateTime occurredAt, JsonNode output) {
    }
}
