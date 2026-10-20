package org.mainthread.rcac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Tag("db2")
class PoolReuseDb2Test {
    @Inject
    DatabasePools pools;

    @Test
    void eachPoolReusesItsOwnPhysicalConnectionAfterErrorsAndRollback() throws Exception {
        Map<PoolIdentity, Long> handles = new EnumMap<>(PoolIdentity.class);
        List<Observation> observations = new ArrayList<>();
        List<PoolIdentity> order = List.of(PoolIdentity.TENANT_A, PoolIdentity.TENANT_B,
                PoolIdentity.TENANT_A_PII, PoolIdentity.TENANT_A);
        for (int round = 0; round < 5; round++) {
            for (PoolIdentity identity : order) {
                Observation observation = observe(identity);
                Long original = handles.putIfAbsent(identity, observation.session().applicationHandle());
                if (original != null) {
                    assertEquals(original.longValue(), observation.session().applicationHandle());
                }
                observations.add(observation);
            }
        }
        assertEquals(3, new HashSet<>(handles.values()).size(), "Pools must not share physical sessions");

        for (PoolIdentity identity : PoolIdentity.values()) {
            try (Connection connection = pools.open(identity);
                    Statement statement = connection.createStatement()) {
                String otherUser = identity == PoolIdentity.TENANT_B ? "TENA" : "TENB";
                SQLException deniedSwitch = assertThrows(SQLException.class,
                        () -> statement.execute("SET SESSION AUTHORIZATION " + otherUser));
                Db2Support.assertAuthorizationFailure(deniedSwitch);
                Db2Support.assertIdentity(Db2Support.session(connection), identity);
            }
            try (Connection connection = pools.open(identity)) {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    SQLException failure = assertThrows(SQLException.class,
                            () -> statement.executeQuery("SELECT * FROM APP.NO_SUCH_TABLE"));
                    assertEquals("42704", failure.getSQLState());
                } finally {
                    connection.rollback();
                }
            }
            Observation afterFailure = observe(identity);
            assertEquals(handles.get(identity).longValue(), afterFailure.session().applicationHandle());
            observations.add(afterFailure);
        }
        Db2Support.evidence("pool-reuse.json", observations);
    }

    @Test
    void concurrentBorrowsKeepTheFixedIdentityAndMasking() throws Exception {
        List<Callable<Observation>> work = new ArrayList<>();
        for (int round = 0; round < 4; round++) {
            for (PoolIdentity identity : PoolIdentity.values()) {
                work.add(() -> observe(identity));
            }
        }
        List<Observation> observations = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var result : executor.invokeAll(work, 60, TimeUnit.SECONDS)) {
                assertTrue(!result.isCancelled(), "Concurrent borrow did not finish");
                observations.add(result.get());
            }
        }
        Map<PoolIdentity, Long> handles = new EnumMap<>(PoolIdentity.class);
        for (Observation observation : observations) {
            Long original = handles.putIfAbsent(observation.identity(), observation.session().applicationHandle());
            if (original != null) {
                assertEquals(original.longValue(), observation.session().applicationHandle());
            }
        }
        assertEquals(3, new HashSet<>(handles.values()).size());
        Db2Support.evidence("pool-concurrency.json", observations);
    }

    private Observation observe(PoolIdentity identity) throws SQLException {
        try (Connection connection = pools.open(identity)) {
            assertTrue(connection.getAutoCommit(), "Agroal must restore autocommit after rollback");
            Db2Support.Session session = Db2Support.session(connection);
            Db2Support.assertIdentity(session, identity);
            List<Customer> customers = Db2Support.customers(connection);
            Db2Support.assertCustomers(customers, identity);
            return new Observation(identity, session, customers.stream().map(Customer::id).toList(),
                    customers.getFirst().email() == null);
        }
    }

    record Observation(PoolIdentity identity, Db2Support.Session session, List<Long> customerIds, boolean masked) {
    }
}
