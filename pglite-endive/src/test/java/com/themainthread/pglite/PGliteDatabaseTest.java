package com.themainthread.pglite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PGliteDatabaseTest {
    @Inject
    PGliteFactory factory;

    @TempDir
    Path directory;

    @Test
    void initializesFreshDatabaseAndQueriesRealPostgres() {
        try (var database = factory.open(directory)) {
            assertTrue(Files.notExists(directory.resolve("CURRENT")));
            var result = database.execute("SELECT version(), 40 + 2 AS answer");
            assertTrue(result.statements().getFirst().rows().getFirst().getFirst().contains("PostgreSQL 17.5"));
            assertEquals("42", result.statements().getFirst().rows().getFirst().get(1));
            assertEquals("I", result.transactionStatus());
        }
        assertTrue(Files.exists(directory.resolve("CURRENT")));
    }

    @Test
    void executesDdlDmlMultipleStatementsUnicodeAndNulls() {
        try (var database = factory.open(directory)) {
            var result = database.execute("CREATE TABLE samples (id int PRIMARY KEY, name text); "
                    + "INSERT INTO samples VALUES (1, 'Grüße 👋'), (2, NULL); SELECT * FROM samples ORDER BY id");
            assertEquals(3, result.statements().size());
            assertEquals("INSERT 0 2", result.statements().get(1).command());
            assertEquals(List.of("id", "name"), result.statements().get(2).columns());
            assertEquals("Grüße 👋", result.statements().get(2).rows().getFirst().get(1));
            assertNull(result.statements().get(2).rows().get(1).get(1));
        }
    }

    @Test
    void commitsAndRollsBackCallbackTransactions() {
        try (var database = factory.open(directory)) {
            database.execute("CREATE TABLE accounts (id int PRIMARY KEY, balance int)");
            String value = database.transaction(session -> {
                session.execute("INSERT INTO accounts VALUES (1, 100)");
                return scalar(session.execute("SELECT balance FROM accounts WHERE id = 1"));
            });
            assertEquals("100", value);
            assertThrows(IllegalArgumentException.class, () -> database.transaction(session -> {
                session.execute("UPDATE accounts SET balance = 0 WHERE id = 1");
                throw new IllegalArgumentException("cancel transfer");
            }));
            assertEquals("100", scalar(database.execute("SELECT balance FROM accounts WHERE id = 1")));
            assertEquals("I", database.transactionStatus());
        }
    }

    @Test
    void recoversFromSyntaxConstraintAndArithmeticErrorsOnSameInstance() {
        try (var database = factory.open(directory)) {
            UUID instance = database.instanceId();
            database.execute("CREATE TABLE keys (id int PRIMARY KEY); INSERT INTO keys VALUES (1)");
            for (int i = 0; i < 100; i++) {
                assertSqlState(database, "SELEC broken", "42601");
                assertEquals("42", scalar(database.execute("SELECT 42")));
                assertSqlState(database, "INSERT INTO keys VALUES (1)", "23505");
                assertSqlState(database, "SELECT 1 / 0", "22012");
                assertEquals("1", scalar(database.execute("SELECT count(*) FROM keys")));
                assertEquals(instance, database.instanceId());
            }
        }
    }

    @Test
    void preservesAbortedTransactionSemanticsAndRecoversAfterRollback() {
        try (var database = factory.open(directory)) {
            UUID instance = database.instanceId();
            database.execute("CREATE TABLE changes (id int PRIMARY KEY)");
            database.execute("BEGIN; INSERT INTO changes VALUES (1)");
            assertSqlState(database, "INSERT INTO changes VALUES (1)", "23505");
            assertEquals("E", database.transactionStatus());
            assertSqlState(database, "SELECT 42", "25P02");
            database.execute("ROLLBACK");
            assertEquals("0", scalar(database.execute("SELECT count(*) FROM changes")));
            assertEquals("42", scalar(database.execute("SELECT 42")));
            assertEquals(instance, database.instanceId());
            assertThrows(SqlException.class, () -> database.transaction(session -> {
                session.execute("INSERT INTO changes VALUES (2)");
                session.execute("SELECT 1 / 0");
                return null;
            }));
            assertEquals("0", scalar(database.execute("SELECT count(*) FROM changes")));
        }
    }

    @Test
    void persistsCommittedDataIntoFreshWasmInstanceAndRollsBackOpenTransactionOnClose() {
        UUID previous;
        String databaseName;
        long transactionId;
        try (var database = factory.open(directory)) {
            previous = database.instanceId();
            databaseName = scalar(database.execute("SELECT current_database()"));
            database.execute("CREATE TABLE saved (id int PRIMARY KEY, value text)");
            database.transaction(session -> session.execute("INSERT INTO saved VALUES (1, 'persisted')"));
            assertSqlState(database, "SELECT nonexistent FROM saved", "42703");
            transactionId = Long.parseLong(scalar(database.execute("SELECT txid_current()")));
            database.execute("BEGIN; INSERT INTO saved VALUES (2, 'uncommitted')");
        }
        try (var database = factory.open(directory)) {
            assertNotEquals(previous, database.instanceId());
            assertEquals(databaseName, scalar(database.execute("SELECT current_database()")));
            assertTrue(Long.parseLong(scalar(database.execute("SELECT txid_current()"))) > transactionId,
                    "Restored transaction ID must advance past committed work");
            assertEquals("\"$user\", public", scalar(database.execute("SELECT current_setting('search_path')")));
            assertEquals("persisted", scalar(database.execute("SELECT value FROM saved")));
            assertEquals("1", scalar(database.execute("SELECT count(*) FROM saved")));
            assertSqlState(database, "SELECT 1 / 0", "22012");
            database.transaction(session -> session.execute("INSERT INTO saved VALUES (3, 'second restart')"));
        }
        try (var database = factory.open(directory)) {
            assertEquals("2", scalar(database.execute("SELECT count(*) FROM saved")));
        }
    }

    @Test
    void serializesConcurrentTransactionsAndRejectsDuplicateDirectoryOwners() throws Exception {
        try (var database = factory.open(directory)) {
            database.execute("CREATE TABLE counter (value int); INSERT INTO counter VALUES (0)");
            assertThrows(java.io.UncheckedIOException.class, () -> factory.open(directory));
            try (var executor = Executors.newFixedThreadPool(4)) {
                var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
                for (int i = 0; i < 20; i++) {
                    tasks.add(() -> {
                        database.transaction(session -> session.execute("UPDATE counter SET value = value + 1"));
                        return null;
                    });
                }
                for (var future : executor.invokeAll(tasks)) {
                    future.get();
                }
            }
            assertEquals("20", scalar(database.execute("SELECT value FROM counter")));
        }
    }

    private static String scalar(QueryResult result) {
        return result.statements().getLast().rows().getFirst().getFirst();
    }

    private static void assertSqlState(PGliteDatabase database, String sql, String expected) {
        SqlException failure = assertThrows(SqlException.class, () -> database.execute(sql));
        assertEquals(expected, failure.sqlState());
    }
}
