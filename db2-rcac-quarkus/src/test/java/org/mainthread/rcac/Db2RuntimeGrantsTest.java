package org.mainthread.rcac;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("db2")
class Db2RuntimeGrantsTest {
    @Test
    void allApplicationDatabaseIdentitiesRemainReadOnly() throws Exception {
        String url = Db2Support.requiredEnvironment("DB2_LAB_JDBC_URL");
        for (PoolIdentity identity : PoolIdentity.values()) {
            String key = switch (identity) {
                case TENANT_A -> "DB2_A_PASSWORD";
                case TENANT_B -> "DB2_B_PASSWORD";
                case TENANT_A_PII -> "DB2_A_PII_PASSWORD";
            };
            try (Connection connection = DriverManager.getConnection(url, identity.databaseUser(),
                    Db2Support.requiredEnvironment(key))) {
                org.junit.jupiter.api.Assertions.assertEquals("RCACLAB", Db2Support.session(connection).database());
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    for (String sql : List.of(
                            "INSERT INTO APP.CUSTOMERS VALUES (999,'" + identity.tenant()
                                    + "','Read-only probe',NULL,1.00,'ACTIVE')",
                            "UPDATE APP.CUSTOMERS SET STATUS='PROBE' WHERE CUSTOMER_ID=-1",
                            "DELETE FROM APP.CUSTOMERS WHERE CUSTOMER_ID=-1")) {
                        SQLException failure = assertThrows(SQLException.class, () -> statement.executeUpdate(sql));
                        Db2Support.assertAuthorizationFailure(failure);
                        connection.rollback();
                    }
                } finally {
                    connection.rollback();
                }
            }
        }
    }
}
