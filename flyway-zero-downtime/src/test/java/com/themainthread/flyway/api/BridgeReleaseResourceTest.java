package com.themainthread.flyway.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.themainthread.flyway.BridgeReleaseProfile;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(BridgeReleaseProfile.class)
class BridgeReleaseResourceTest {

    @Inject
    DataSource dataSource;

    @BeforeEach
    void clearCustomers() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("TRUNCATE customer RESTART IDENTITY")) {
            statement.executeUpdate();
        }
    }

    @Test
    void bridgeWritesBothColumns() throws SQLException {
        long id = given()
                .contentType("application/json")
                .body("""
                        {"email":"ada@example.com","displayName":"Ada Lovelace"}
                        """)
                .when()
                .post("/customers")
                .then()
                .statusCode(201)
                .body("email", equalTo("ada@example.com"))
                .body("displayName", equalTo("Ada Lovelace"))
                .extract()
                .jsonPath()
                .getLong("id");

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT full_name, display_name FROM customer WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertEquals("Ada Lovelace", result.getString("full_name"));
                assertEquals("Ada Lovelace", result.getString("display_name"));
            }
        }
    }

    @Test
    void bridgeSeesWritesFromLegacyRelease() throws SQLException {
        long id;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO customer (email, full_name) VALUES (?, ?) RETURNING id")) {
            insert.setString(1, "grace@example.com");
            insert.setString(2, "Grace Hopper");
            try (ResultSet result = insert.executeQuery()) {
                result.next();
                id = result.getLong("id");
            }
        }

        given()
                .when()
                .get("/customers/{id}", id)
                .then()
                .statusCode(200)
                .body("displayName", equalTo("Grace Hopper"));

        try (Connection connection = dataSource.getConnection();
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE customer SET full_name = ? WHERE id = ?")) {
            update.setString(1, "Rear Admiral Grace Hopper");
            update.setLong(2, id);
            update.executeUpdate();
        }

        given()
                .when()
                .get("/customers/{id}", id)
                .then()
                .statusCode(200)
                .body("displayName", equalTo("Rear Admiral Grace Hopper"));
    }
}
