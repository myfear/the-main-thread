package com.themainthread.flyway.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import com.themainthread.flyway.config.MigrationDemoConfig;
import com.themainthread.flyway.domain.Customer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CustomerRepository {

    private final DataSource dataSource;
    private final MigrationDemoConfig config;

    @Inject
    public CustomerRepository(DataSource dataSource, MigrationDemoConfig config) {
        this.dataSource = dataSource;
        this.config = config;
    }

    public Customer create(String email, String displayName) {
        String sql = switch (config.release()) {
            case LEGACY -> """
                    INSERT INTO customer (email, full_name)
                    VALUES (?, ?)
                    RETURNING id, email, full_name AS display_name
                    """;
            case BRIDGE -> """
                    INSERT INTO customer (email, full_name, display_name)
                    VALUES (?, ?, ?)
                    RETURNING id, email, COALESCE(display_name, full_name) AS display_name
                    """;
            case MODERN -> """
                    INSERT INTO customer (email, display_name)
                    VALUES (?, ?)
                    RETURNING id, email, display_name
                    """;
        };

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setString(2, displayName);
            if (config.release() == MigrationDemoConfig.Release.BRIDGE) {
                statement.setString(3, displayName);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return mapCustomer(result);
            }
        } catch (SQLException exception) {
            throw new DatabaseOperationException("Could not create customer", exception);
        }
    }

    public Optional<Customer> findById(long id) {
        String sql = switch (config.release()) {
            case LEGACY -> "SELECT id, email, full_name AS display_name FROM customer WHERE id = ?";
            case BRIDGE -> "SELECT id, email, COALESCE(display_name, full_name) AS display_name FROM customer WHERE id = ?";
            case MODERN -> "SELECT id, email, display_name FROM customer WHERE id = ?";
        };

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapCustomer(result));
            }
        } catch (SQLException exception) {
            throw new DatabaseOperationException("Could not read customer " + id, exception);
        }
    }

    public Optional<Customer> rename(long id, String displayName) {
        String sql = switch (config.release()) {
            case LEGACY -> """
                    UPDATE customer
                    SET full_name = ?
                    WHERE id = ?
                    RETURNING id, email, full_name AS display_name
                    """;
            case BRIDGE -> """
                    UPDATE customer
                    SET full_name = ?, display_name = ?
                    WHERE id = ?
                    RETURNING id, email, COALESCE(display_name, full_name) AS display_name
                    """;
            case MODERN -> """
                    UPDATE customer
                    SET display_name = ?
                    WHERE id = ?
                    RETURNING id, email, display_name
                    """;
        };

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, displayName);
            if (config.release() == MigrationDemoConfig.Release.BRIDGE) {
                statement.setString(2, displayName);
                statement.setLong(3, id);
            } else {
                statement.setLong(2, id);
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapCustomer(result));
            }
        } catch (SQLException exception) {
            throw new DatabaseOperationException("Could not rename customer " + id, exception);
        }
    }

    private Customer mapCustomer(ResultSet result) throws SQLException {
        return new Customer(
                result.getLong("id"),
                result.getString("email"),
                result.getString("display_name"));
    }
}
