package org.mainthread.rcac;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomerRepository {
    private final DatabasePools pools;

    public CustomerRepository(DatabasePools pools) {
        this.pools = pools;
    }

    public List<Customer> list(PoolIdentity identity) throws SQLException {
        try (Connection connection = pools.open(identity);
                PreparedStatement statement = connection.prepareStatement(CustomerQueries.LIST)) {
            statement.setQueryTimeout(15);
            try (ResultSet rows = statement.executeQuery()) {
                List<Customer> customers = new ArrayList<>();
                while (rows.next()) {
                    customers.add(read(rows));
                }
                return List.copyOf(customers);
            }
        }
    }

    public Optional<Customer> find(PoolIdentity identity, long id) throws SQLException {
        try (Connection connection = pools.open(identity);
                PreparedStatement statement = connection.prepareStatement(CustomerQueries.FIND)) {
            statement.setLong(1, id);
            statement.setQueryTimeout(15);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        }
    }

    static Customer read(ResultSet rows) throws SQLException {
        return new Customer(rows.getLong("CUSTOMER_ID"), rows.getString("TENANT_ID"),
                rows.getString("DISPLAY_NAME"), rows.getString("EMAIL"),
                rows.getBigDecimal("CREDIT_LIMIT"), rows.getString("STATUS"));
    }
}
