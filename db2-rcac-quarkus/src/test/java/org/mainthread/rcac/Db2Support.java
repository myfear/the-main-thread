package org.mainthread.rcac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.ConfigProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

final class Db2Support {
    private Db2Support() {
    }

    static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = ConfigProvider.getConfig().getOptionalValue(name, String.class).orElse(null);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + name + "; use scripts/verify-db2.sh or scripts/test-db2.sh.");
        }
        return value;
    }

    static Connection connect(PoolIdentity identity) throws SQLException {
        String passwordKey = switch (identity) {
            case TENANT_A -> "DB2_A_PASSWORD";
            case TENANT_B -> "DB2_B_PASSWORD";
            case TENANT_A_PII -> "DB2_A_PII_PASSWORD";
        };
        return DriverManager.getConnection(requiredEnvironment("DB2_JDBC_URL"), identity.databaseUser(),
                requiredEnvironment(passwordKey));
    }

    static Connection unmappedConnection() throws SQLException {
        return DriverManager.getConnection(requiredEnvironment("DB2_JDBC_URL"), "tennone",
                requiredEnvironment("DB2_UNMAPPED_PASSWORD"));
    }

    static long count(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(15);
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), "Scalar result is missing");
                return rows.getLong(1);
            }
        }
    }

    static List<Customer> customers(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CustomerQueries.LIST)) {
            statement.setQueryTimeout(15);
            try (ResultSet rows = statement.executeQuery()) {
                List<Customer> customers = new ArrayList<>();
                while (rows.next()) {
                    customers.add(CustomerRepository.read(rows));
                }
                return List.copyOf(customers);
            }
        }
    }

    static void assertCustomers(List<Customer> customers, PoolIdentity identity) {
        List<Long> expectedIds = identity == PoolIdentity.TENANT_B ? List.of(201L, 202L, 203L)
                : List.of(101L, 102L, 103L);
        assertEquals(expectedIds, customers.stream().map(Customer::id).toList());
        for (Customer customer : customers) {
            assertEquals(identity.tenant(), customer.tenantId());
            assertEquals(identity.sensitiveAccess(), customer.email() != null);
            assertEquals(identity.sensitiveAccess(), customer.creditLimit() != null);
        }
    }

    static Session session(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "VALUES (SESSION_USER, SYSTEM_USER, SYSPROC.MON_GET_APPLICATION_HANDLE(), CURRENT SERVER)")) {
            assertTrue(rows.next());
            return new Session(rows.getString(1).strip(), rows.getString(2).strip(), rows.getLong(3),
                    rows.getString(4).strip());
        }
    }

    static void assertIdentity(Session session, PoolIdentity identity) {
        assertEquals(identity.databaseUser(), session.sessionUser());
        assertEquals(identity.databaseUser(), session.systemUser());
        assertTrue(session.applicationHandle() > 0);
    }

    static void assertAuthorizationFailure(SQLException failure) {
        assertNotNull(failure.getSQLState());
        boolean insufficientPrivilege = "42501".equals(failure.getSQLState()) && failure.getErrorCode() == -551;
        boolean impersonationDenied = "28000".equals(failure.getSQLState()) && failure.getErrorCode() == -552;
        assertTrue(insufficientPrivilege || impersonationDenied,
                () -> "Expected authorization failure, got " + failure.getSQLState() + "/" + failure.getErrorCode());
    }

    static void evidence(String name, Object value) throws IOException {
        Path directory = Path.of(System.getProperty("rcac.evidence-dir", "eval/latest"));
        Files.createDirectories(directory);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(directory.resolve(name).toFile(), value);
    }

    static void assertIntegrationDatabase(Connection connection) throws SQLException {
        assertEquals("RCACIT", session(connection).database(), "Write probes must run only in RCACIT");
        assertFalse(connection.isReadOnly());
    }

    record Session(String sessionUser, String systemUser, long applicationHandle, String database) {
    }

    record Failure(String scenario, String sqlState, int sqlCode) {
        static Failure from(String scenario, SQLException failure) {
            return new Failure(scenario, failure.getSQLState(), failure.getErrorCode());
        }
    }
}
