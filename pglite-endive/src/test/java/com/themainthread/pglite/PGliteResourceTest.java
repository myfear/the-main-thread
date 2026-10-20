package com.themainthread.pglite;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class PGliteResourceTest {
    @Test
    void commitsRollsBackAndRecoversThroughHttpOnSameInstance() {
        String instance = given().get("/pglite").then().statusCode(200).extract().path("instanceId");
        String table = "http_" + UUID.randomUUID().toString().replace("-", "");
        query("CREATE TABLE " + table + " (id int PRIMARY KEY)");
        given().contentType("application/json")
                .body(Map.of("statements", List.of("INSERT INTO " + table + " VALUES (1)",
                        "INSERT INTO " + table + " VALUES (2)")))
                .post("/pglite/transaction").then().statusCode(200);
        given().contentType("application/json")
                .body(Map.of("statements", List.of("INSERT INTO " + table + " VALUES (3)",
                        "INSERT INTO " + table + " VALUES (1)")))
                .post("/pglite/transaction").then().statusCode(400).body("sqlState", equalTo("23505"));
        given().contentType("application/json").body(Map.of("sql", "SELEC broken"))
                .post("/pglite/sql").then().statusCode(400).body("sqlState", equalTo("42601"));
        given().contentType("application/json").body(Map.of("sql", "SELECT count(*) FROM " + table))
                .post("/pglite/sql").then().statusCode(200).body("statements[0].rows[0][0]", equalTo("2"));
        given().get("/pglite").then().statusCode(200).body("instanceId", equalTo(instance))
                .body("transactionStatus", equalTo("I"));
    }

    private static void query(String sql) {
        given().contentType("application/json").body(Map.of("sql", sql))
                .post("/pglite/sql").then().statusCode(200);
    }
}
