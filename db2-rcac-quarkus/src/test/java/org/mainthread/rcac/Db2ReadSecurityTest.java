package org.mainthread.rcac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("db2")
class Db2ReadSecurityTest {
    @Test
    void unfilteredJdbcQueriesAreIsolatedAndMaskedByDb2() throws Exception {
        List<Object> observations = new ArrayList<>();
        for (PoolIdentity identity : PoolIdentity.values()) {
            try (Connection connection = Db2Support.connect(identity)) {
                assertTrue(connection.getMetaData().getDatabaseProductName().toUpperCase().contains("DB2"));
                String version = connection.getMetaData().getDatabaseProductVersion();
                assertTrue(version.contains("12015") || version.contains("12.1.5"), version);
                Db2Support.Session session = Db2Support.session(connection);
                Db2Support.assertIdentity(session, identity);
                List<Customer> customers = Db2Support.customers(connection);
                Db2Support.assertCustomers(customers, identity);
                assertEquals(3, Db2Support.count(connection, "SELECT COUNT(*) FROM APP.CUSTOMERS"));
                long hiddenId = identity == PoolIdentity.TENANT_B ? 101 : 201;
                assertEquals(0, Db2Support.count(connection,
                        "SELECT COUNT(*) FROM APP.CUSTOMERS WHERE CUSTOMER_ID = ?", hiddenId));
                assertEquals(3, Db2Support.count(connection,
                        "SELECT COUNT(*) FROM APP.CUSTOMERS WHERE CUSTOMER_ID = ? OR 1=1", hiddenId));
                if (identity.sensitiveAccess()) {
                    assertEquals("aster@example.invalid", customers.getFirst().email());
                    assertEquals(new BigDecimal("12000.00"), customers.getFirst().creditLimit());
                }
                observations.add(Map.of("identity", identity.name(), "session", session,
                        "customers", customers, "serverVersion", version,
                        "driverVersion", connection.getMetaData().getDriverVersion()));
            }
        }
        try (Connection connection = Db2Support.unmappedConnection()) {
            assertEquals("TENNONE", Db2Support.session(connection).sessionUser());
            assertEquals(0, Db2Support.count(connection, "SELECT COUNT(*) FROM APP.CUSTOMERS"));
            assertTrue(Db2Support.customers(connection).isEmpty());
        }
        Db2Support.evidence("jdbc-read-security.json", observations);
    }

    @Test
    void atReadMasksAlsoApplyToPredicates() throws Exception {
        for (PoolIdentity identity : PoolIdentity.values()) {
            try (Connection connection = Db2Support.connect(identity)) {
                long expected = identity.sensitiveAccess() ? 1 : 0;
                String ownTenantEmail = identity == PoolIdentity.TENANT_B ? "delta@example.invalid"
                        : "aster@example.invalid";
                assertEquals(expected, Db2Support.count(connection,
                        "SELECT COUNT(*) FROM APP.CUSTOMERS WHERE EMAIL = ?", ownTenantEmail));
                assertEquals(expected, Db2Support.count(connection,
                        "SELECT COUNT(*) FROM APP.CUSTOMERS WHERE CREDIT_LIMIT > ?", new BigDecimal("10000.00")));
                assertEquals(identity.sensitiveAccess() ? 0 : 3, Db2Support.count(connection,
                        "SELECT COUNT(*) FROM APP.CUSTOMERS WHERE EMAIL IS NULL AND CREDIT_LIMIT IS NULL"));
            }
        }
    }

    @Test
    void runtimeAccountsCannotChangeTheSecurityBoundary() throws Exception {
        List<Db2Support.Failure> observations = new ArrayList<>();
        List<String> statements = List.of(
                "UPDATE LABSEC.TENANT_PRINCIPALS SET TENANT_ID='B' WHERE AUTHID='TENA'",
                "ALTER TABLE APP.CUSTOMERS DEACTIVATE ROW ACCESS CONTROL",
                "ALTER TABLE APP.CUSTOMERS DEACTIVATE COLUMN ACCESS CONTROL",
                "ALTER MASK LABSEC.EMAIL_MASK DISABLE",
                "GRANT ROLE RCAC_PII TO USER TENB");
        for (PoolIdentity identity : PoolIdentity.values()) {
            for (String sql : statements) {
                try (Connection connection = Db2Support.connect(identity);
                        Statement statement = connection.createStatement()) {
                    SQLException failure = assertThrows(SQLException.class, () -> statement.execute(sql));
                    Db2Support.assertAuthorizationFailure(failure);
                    observations.add(Db2Support.Failure.from(identity.name() + ": " + sql, failure));
                }
            }
            for (String target : List.of("TENA", "TENB", "TENAPII", "DB2INST1")) {
                if (target.equals(identity.databaseUser())) {
                    continue;
                }
                try (Connection connection = Db2Support.connect(identity);
                        Statement statement = connection.createStatement()) {
                    SQLException failure = assertThrows(SQLException.class,
                            () -> statement.execute("SET SESSION AUTHORIZATION " + target));
                    Db2Support.assertAuthorizationFailure(failure);
                    Db2Support.assertIdentity(Db2Support.session(connection), identity);
                    observations.add(Db2Support.Failure.from(identity.name() + ": impersonation of " + target, failure));
                }
            }
        }
        Db2Support.evidence("jdbc-privilege-denials.json", observations);
    }
}
