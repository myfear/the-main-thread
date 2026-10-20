package org.mainthread.rcac;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
class PoolIdentityVerifier {
    private final DatabasePools pools;

    PoolIdentityVerifier(DatabasePools pools) {
        this.pools = pools;
    }

    void verify(@Observes StartupEvent event) throws SQLException {
        for (PoolIdentity identity : PoolIdentity.values()) {
            try (Connection connection = pools.open(identity);
                    Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery(
                            "VALUES (SESSION_USER, SYSTEM_USER, VERIFY_ROLE_FOR_USER(SESSION_USER, 'RCAC_PII'))")) {
                if (!rows.next() || !identity.databaseUser().equals(rows.getString(1).strip())
                        || !identity.databaseUser().equals(rows.getString(2).strip())
                        || identity.sensitiveAccess() != (rows.getInt(3) == 1)) {
                    throw new IllegalStateException("Unexpected Db2 identity or role for " + identity);
                }
            }
        }
    }
}
