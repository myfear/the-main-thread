package com.themainthread.ledger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class LedgerSchema {

    private final DataSource dataSource;

    public LedgerSchema(@ObservedDataSource DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void initialize(@Observes StartupEvent event) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ledger_account (
                        account_id VARCHAR(64) PRIMARY KEY,
                        balance NUMERIC(19, 2) NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO ledger_account (account_id, balance)
                    VALUES ('acct-42', 1250.00)
                    ON CONFLICT (account_id) DO NOTHING
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize the ledger schema", exception);
        }
    }
}
