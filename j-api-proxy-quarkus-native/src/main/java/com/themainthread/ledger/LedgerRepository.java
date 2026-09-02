package com.themainthread.ledger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class LedgerRepository {

    private final DataSource dataSource;

    public LedgerRepository(@ObservedDataSource DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public BigDecimal balance(String accountId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT balance FROM ledger_account WHERE account_id = ?")) {
            statement.setString(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new NotFoundException("Unknown account: " + accountId);
                }
                return resultSet.getBigDecimal("balance");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read ledger account " + accountId, exception);
        }
    }
}
