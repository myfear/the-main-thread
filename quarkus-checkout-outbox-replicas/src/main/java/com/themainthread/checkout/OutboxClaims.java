package com.themainthread.checkout;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OutboxClaims {

    // A nullable ID limits explicit demo/test publication without a second claim algorithm.
    static final String CLAIM_SQL = """
            WITH candidates AS (
                SELECT id
                FROM outbox_event
                WHERE published_at IS NULL
                  AND (claimed_until IS NULL OR claimed_until <= CURRENT_TIMESTAMP)
                  AND (CAST(? AS uuid) IS NULL OR id = ?)
                ORDER BY created_at, id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            UPDATE outbox_event AS event
            SET claimed_by = ?,
                claim_token = gen_random_uuid(),
                claimed_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'),
                attempt_count = event.attempt_count + 1
            FROM candidates
            WHERE event.id = candidates.id
            RETURNING event.id, event.aggregateid, event.payload,
                      event.claim_token, event.claimed_until
            """;

    private final AgroalDataSource dataSource;

    OutboxClaims(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<OutboxClaim> claim(String worker, int batchSize, Duration lease, UUID onlyId) {
        if (batchSize < 1 || batchSize > 20 || lease.compareTo(Duration.ofSeconds(1)) < 0
                || lease.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("Use batch-size 1..20 and lease-duration 1s..10m");
        }
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(CLAIM_SQL)) {
            statement.setObject(1, onlyId);
            statement.setObject(2, onlyId);
            statement.setInt(3, batchSize);
            statement.setString(4, worker);
            statement.setLong(5, lease.toMillis());
            List<OutboxClaim> claims = new ArrayList<>();
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    claims.add(new OutboxClaim(rows.getObject("id", UUID.class), rows.getString("aggregateid"),
                            rows.getString("payload"), rows.getObject("claim_token", UUID.class),
                            rows.getTimestamp("claimed_until").toInstant()));
                }
            }
            return claims;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not claim outbox rows", e);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int complete(UUID id, UUID token) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        UPDATE outbox_event
                        SET published_at = CURRENT_TIMESTAMP,
                            claimed_by = NULL, claim_token = NULL, claimed_until = NULL
                        WHERE id = ? AND claim_token = ? AND published_at IS NULL
                        """)) {
            statement.setObject(1, id);
            statement.setObject(2, token);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not complete outbox row " + id, e);
        }
    }
}
