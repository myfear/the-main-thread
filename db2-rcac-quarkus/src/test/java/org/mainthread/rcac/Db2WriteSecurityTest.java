package org.mainthread.rcac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("db2")
class Db2WriteSecurityTest {
    @Test
    void permittedWritesSucceedAndHiddenRowsRemainUnchanged() throws Exception {
        try (Connection connection = Db2Support.connect(PoolIdentity.TENANT_A_PII)) {
            Db2Support.assertIntegrationDatabase(connection);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertEquals(1, statement.executeUpdate(
                        "INSERT INTO APP.CUSTOMERS VALUES (901,'A','Write probe',NULL,1.00,'ACTIVE')"));
                assertEquals(1, statement.executeUpdate("UPDATE APP.CUSTOMERS SET STATUS='PROBE' WHERE CUSTOMER_ID=901"));
                assertEquals(1, Db2Support.count(connection,
                        "SELECT COUNT(*) FROM APP.CUSTOMERS WHERE CUSTOMER_ID=901 AND STATUS='PROBE'"));
                assertEquals(1, statement.executeUpdate("DELETE FROM APP.CUSTOMERS WHERE CUSTOMER_ID=901"));
                assertEquals(0, statement.executeUpdate("UPDATE APP.CUSTOMERS SET STATUS='PROBE' WHERE CUSTOMER_ID=201"));
                assertEquals(0, statement.executeUpdate("DELETE FROM APP.CUSTOMERS WHERE CUSTOMER_ID=201"));
            } finally {
                connection.rollback();
            }
        }
        try (Connection connection = Db2Support.connect(PoolIdentity.TENANT_B)) {
            assertEquals(1, Db2Support.count(connection,
                    "SELECT COUNT(*) FROM APP.CUSTOMERS WHERE CUSTOMER_ID=201 AND STATUS='ACTIVE'"));
            Db2Support.assertCustomers(Db2Support.customers(connection), PoolIdentity.TENANT_B);
        }
    }

    @Test
    void newRowChecksRejectCrossTenantInsertAndTenantTransfer() throws Exception {
        List<Db2Support.Failure> failures = new ArrayList<>();
        for (String sql : List.of(
                "INSERT INTO APP.CUSTOMERS VALUES (902,'B','Forbidden probe',NULL,1.00,'ACTIVE')",
                "UPDATE APP.CUSTOMERS SET TENANT_ID='B' WHERE CUSTOMER_ID=101")) {
            try (Connection connection = Db2Support.connect(PoolIdentity.TENANT_A_PII)) {
                Db2Support.assertIntegrationDatabase(connection);
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    SQLException failure = assertThrows(SQLException.class, () -> statement.executeUpdate(sql));
                    assertEquals("22542", failure.getSQLState(), "Expected the RCAC new-row check, not a privilege or syntax error");
                    assertEquals(-20471, failure.getErrorCode());
                    assertEquals(1, Db2Support.count(connection,
                            "SELECT COUNT(*) FROM APP.CUSTOMERS WHERE CUSTOMER_ID=101 AND TENANT_ID='A'"));
                    assertEquals(0, Db2Support.count(connection,
                            "SELECT COUNT(*) FROM APP.CUSTOMERS WHERE CUSTOMER_ID=902"));
                    failures.add(Db2Support.Failure.from(sql, failure));
                } finally {
                    connection.rollback();
                }
                connection.setAutoCommit(true);
                Db2Support.assertCustomers(Db2Support.customers(connection), PoolIdentity.TENANT_A_PII);
            }
        }
        try (Connection connection = Db2Support.connect(PoolIdentity.TENANT_B)) {
            assertEquals(0, Db2Support.count(connection,
                    "SELECT COUNT(*) FROM APP.CUSTOMERS WHERE CUSTOMER_ID=902"));
            Db2Support.assertCustomers(Db2Support.customers(connection), PoolIdentity.TENANT_B);
        }
        Db2Support.evidence("jdbc-write-denials.json", failures);
    }

    @Test
    void defaultAtResultMaskStillAllowsPredicateInference() throws Exception {
        try (Connection connection = Db2Support.connect(PoolIdentity.TENANT_A)) {
            Db2Support.assertIntegrationDatabase(connection);
            assertEquals(1, Db2Support.count(connection,
                    "SELECT COUNT(*) FROM APP.MASK_RESULT_EXAMPLE WHERE CREDIT_LIMIT > 10000"));
            assertEquals(1, Db2Support.count(connection,
                    "SELECT COUNT(*) FROM APP.MASK_RESULT_EXAMPLE WHERE CREDIT_LIMIT = 12000"));
            assertEquals(0, Db2Support.count(connection,
                    "SELECT COUNT(*) FROM APP.CUSTOMERS WHERE CREDIT_LIMIT > 10000"));
            try (Statement statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT CREDIT_LIMIT FROM APP.MASK_RESULT_EXAMPLE")) {
                org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                org.junit.jupiter.api.Assertions.assertNull(rows.getBigDecimal(1));
            }
            Db2Support.evidence("mask-semantics.json", Map.of(
                    "atResultThresholdMatches", 1, "atReadThresholdMatches", 0,
                    "visibleCreditLimit", "NULL", "dataset", "fictional integration-only example"));
        }
    }
}
