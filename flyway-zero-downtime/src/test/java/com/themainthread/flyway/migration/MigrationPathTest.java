package com.themainthread.flyway.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.themainthread.flyway.ManualMigrationProfile;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(ManualMigrationProfile.class)
class MigrationPathTest {

    private static final String SAFE_MIGRATIONS = "classpath:db/migration";
    private static final String NAIVE_MIGRATIONS = "classpath:db/naive";

    @Inject
    DataSource dataSource;

    @BeforeEach
    void cleanDatabase() {
        flyway(SAFE_MIGRATIONS, "4").clean();
    }

    @Test
    void expandAndContractPreservesMixedVersionCompatibility() throws SQLException {
        flyway(SAFE_MIGRATIONS, "1").migrate();
        long adaId = insertLegacyCustomer("ada@example.com", "Ada Lovelace");
        long graceId = insertLegacyCustomer("grace@example.com", "Grace Hopper");

        flyway(SAFE_MIGRATIONS, "2").migrate();
        assertNull(columnValue(graceId, "display_name"));

        updateLegacyName(adaId, "Augusta Ada King");
        assertEquals("Augusta Ada King", columnValue(adaId, "display_name"));

        long linusId = insertBridgeCustomer("linus@example.com", "Linus Torvalds");
        assertEquals("Linus Torvalds", columnValue(linusId, "full_name"));
        assertEquals("Linus Torvalds", columnValue(linusId, "display_name"));

        flyway(SAFE_MIGRATIONS, "3").migrate();
        assertEquals("Grace Hopper", columnValue(graceId, "display_name"));

        long margaretId = insertModernCustomer("margaret@example.com", "Margaret Hamilton");
        assertEquals("Margaret Hamilton", columnValue(margaretId, "full_name"));

        flyway(SAFE_MIGRATIONS, "4").migrate();
        assertFalse(columnExists("full_name"));
        assertEquals("Grace Hopper", columnValue(graceId, "display_name"));
        assertEquals("Margaret Hamilton", columnValue(margaretId, "display_name"));
    }

    @Test
    void directRenameBreaksTheLegacyQuery() throws SQLException {
        flyway(NAIVE_MIGRATIONS, "1").migrate();
        long id = insertLegacyCustomer("ada@example.com", "Ada Lovelace");
        assertEquals("Ada Lovelace", columnValue(id, "full_name"));

        flyway(NAIVE_MIGRATIONS, "2").migrate();

        SQLException failure = assertThrows(SQLException.class, () -> columnValue(id, "full_name"));
        assertEquals("42703", failure.getSQLState());
    }

    private Flyway flyway(String location, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .target(MigrationVersion.fromVersion(target))
                .cleanDisabled(false)
                .load();
    }

    private long insertLegacyCustomer(String email, String fullName) throws SQLException {
        return insertCustomer(
                "INSERT INTO customer (email, full_name) VALUES (?, ?) RETURNING id",
                email,
                fullName);
    }

    private long insertBridgeCustomer(String email, String displayName) throws SQLException {
        return insertCustomer(
                "INSERT INTO customer (email, full_name, display_name) VALUES (?, ?, ?) RETURNING id",
                email,
                displayName,
                displayName);
    }

    private long insertModernCustomer(String email, String displayName) throws SQLException {
        return insertCustomer(
                "INSERT INTO customer (email, display_name) VALUES (?, ?) RETURNING id",
                email,
                displayName);
    }

    private long insertCustomer(String sql, String... values) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong("id");
            }
        }
    }

    private void updateLegacyName(long id, String fullName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE customer SET full_name = ? WHERE id = ?")) {
            statement.setString(1, fullName);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private String columnValue(long id, String column) throws SQLException {
        String sql = "SELECT " + column + " FROM customer WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private boolean columnExists(String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'customer'
                          AND column_name = ?
                        """)) {
            statement.setString(1, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
