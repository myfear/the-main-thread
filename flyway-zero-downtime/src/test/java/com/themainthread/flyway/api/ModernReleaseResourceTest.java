package com.themainthread.flyway.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.themainthread.flyway.ModernReleaseProfile;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(ModernReleaseProfile.class)
class ModernReleaseResourceTest {

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
    void modernReleaseUsesOnlyDisplayName() throws SQLException {
        long id = given()
                .contentType("application/json")
                .body("""
                        {"email":"margaret@example.com","displayName":"Margaret Hamilton"}
                        """)
                .when()
                .post("/customers")
                .then()
                .statusCode(201)
                .body("displayName", equalTo("Margaret Hamilton"))
                .extract()
                .jsonPath()
                .getLong("id");

        given()
                .contentType("application/json")
                .body("""
                        {"displayName":"Margaret H. Hamilton"}
                        """)
                .when()
                .put("/customers/{id}/name", id)
                .then()
                .statusCode(200)
                .body("displayName", equalTo("Margaret H. Hamilton"));

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT 1
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'customer'
                          AND column_name = 'full_name'
                        """);
                ResultSet result = statement.executeQuery()) {
            assertFalse(result.next());
        }
    }
}
