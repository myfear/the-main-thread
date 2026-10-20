package com.themainthread.time;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DatabaseExperiments {
    private final DataSource dataSource;

    public DatabaseExperiments(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public ExperimentResult precision(boolean alternative) {
        OffsetDateTime input = OffsetDateTime.parse("2026-12-24T12:00:00.123456789Z");
        int precision = alternative ? 3 : 6;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE precision_probe (value TIMESTAMPTZ(" + precision + ")) ON COMMIT DROP");
            }
            try (var insert = connection.prepareStatement("INSERT INTO precision_probe VALUES (?)")) {
                insert.setObject(1, input);
                insert.executeUpdate();
            }
            OffsetDateTime stored;
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT value FROM precision_probe")) {
                rows.next();
                stored = rows.getObject(1, OffsetDateTime.class);
            }
            connection.rollback();
            return ExperimentResult.of(
                    "This value really passed through PostgreSQL and its JDBC driver. Define a shared precision contract and test the round trip.",
                    "Java input", input, "PostgreSQL column", "TIMESTAMPTZ(" + precision + ")",
                    "JDBC round trip", stored, "Original nanoseconds", input.getNano(),
                    "Stored nanoseconds", stored.getNano(), "Same instant", input.toInstant().equals(stored.toInstant()));
        } catch (SQLException exception) {
            throw new IllegalStateException("PostgreSQL precision experiment failed", exception);
        }
    }

    public ExperimentResult zone(boolean alternative) {
        String region = alternative ? "Asia/Tokyo" : "Europe/Berlin";
        OffsetDateTime input = LocalDate.of(2026, 12, 24).atTime(9, 0).atZone(ZoneId.of(region)).toOffsetDateTime();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SET LOCAL TIME ZONE 'UTC'");
                statement.execute("CREATE TEMP TABLE zone_probe (value TIMESTAMPTZ, region TEXT) ON COMMIT DROP");
            }
            try (var insert = connection.prepareStatement("INSERT INTO zone_probe VALUES (?, ?)")) {
                insert.setObject(1, input);
                insert.setString(2, region);
                insert.executeUpdate();
            }
            String rendered;
            String savedRegion;
            OffsetDateTime stored;
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT value, value::text, region FROM zone_probe")) {
                rows.next();
                stored = rows.getObject(1, OffsetDateTime.class);
                rendered = rows.getString(2);
                savedRegion = rows.getString(3);
            }
            connection.rollback();
            return ExperimentResult.of(
                    "PostgreSQL kept the instant, not the original region. The separate region column is what lets us reconstruct the intended zoned view.",
                    "Submitted value", input, "Original region", region, "Database session zone", "UTC",
                    "Database timestamp text", rendered, "Same instant", input.toInstant().equals(stored.toInstant()),
                    "Separately saved region", savedRegion, "Reconstructed view",
                    stored.toInstant().atZone(ZoneId.of(savedRegion)));
        } catch (SQLException exception) {
            throw new IllegalStateException("PostgreSQL zone experiment failed", exception);
        }
    }

    public UUID createJob(Instant dueAt) {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            try (var cleanup = connection.createStatement()) {
                cleanup.executeUpdate("DELETE FROM scheduled_gift WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '1 day'");
            }
            try (var insert = connection.prepareStatement("INSERT INTO scheduled_gift (id, due_at) VALUES (?, ?)")) {
                insert.setObject(1, id);
                insert.setObject(2, dueAt.atOffset(ZoneOffset.UTC));
                insert.executeUpdate();
            }
            return id;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create the scheduled gift", exception);
        }
    }

    public int attempt(UUID id, Instant now, boolean rollBack) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("""
                    INSERT INTO gift_effect (job_id, delivered_at, message)
                    SELECT id, ?, 'A small gift from the calendar'
                    FROM scheduled_gift
                    WHERE id = ? AND due_at <= ?
                    ON CONFLICT (job_id) DO NOTHING
                    """)) {
                insert.setObject(1, now.atOffset(ZoneOffset.UTC));
                insert.setObject(2, id);
                insert.setObject(3, now.atOffset(ZoneOffset.UTC));
                int inserted = insert.executeUpdate();
                if (rollBack) {
                    connection.rollback();
                } else {
                    connection.commit();
                }
                return inserted;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Scheduled gift transaction failed", exception);
        }
    }

    public int effectCount(UUID id) {
        try (Connection connection = dataSource.getConnection();
                var query = connection.prepareStatement("SELECT count(*) FROM gift_effect WHERE job_id = ?")) {
            query.setObject(1, id);
            try (var rows = query.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not inspect the gift ledger", exception);
        }
    }

    public ExperimentResult recovery(boolean alternative, CalendarTime.Snapshot time) {
        Instant due = time.unlockAt(24);
        Instant wakeUp = due.plusSeconds(alternative ? 3600 : 600);
        UUID id = createJob(due);
        int early = attempt(id, due.minusSeconds(1), false);
        int rolledBack = attempt(id, wakeUp, true);
        int afterRollback = effectCount(id);
        int recovered = attempt(id, wakeUp, false);
        int duplicate = attempt(id, wakeUp.plusSeconds(1), false);
        return ExperimentResult.of(
                "Worker downtime and failure are simulated with explicit instants and a rollback; no process is killed. Every attempt uses a fresh connection. The database really commits one local gift effect. Email or another external side effect still needs an outbox and downstream idempotency.",
                "Job ID (persisted)", id, "Scheduled instant", due, "Simulated wake-up", wakeUp,
                "Equality-only trigger would fire", wakeUp.equals(due), "Attempt before due: inserted rows", early,
                "Failed attempt: rows inserted then rolled back", rolledBack, "Committed effects after rollback", afterRollback,
                "Recovery: newly committed effects", recovered, "Repeated delivery: new effects", duplicate,
                "Final committed effects", effectCount(id));
    }
}
