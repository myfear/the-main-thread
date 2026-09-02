package com.themainthread.ledger;

import java.sql.Connection;
import java.util.concurrent.atomic.LongAdder;

import javax.sql.DataSource;

import io.github.rrobetti.japiproxy.core.InvocationFilter;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JdbcCallMetrics {

    private final LongAdder dataSourceCalls = new LongAdder();
    private final LongAdder connectionCalls = new LongAdder();
    private final LongAdder failures = new LongAdder();

    public InvocationFilter filter() {
        return (invocation, chain) -> {
            try {
                Object result = chain.proceed(invocation);
                if (invocation.interfaceType() == DataSource.class) {
                    dataSourceCalls.increment();
                } else if (invocation.interfaceType() == Connection.class) {
                    connectionCalls.increment();
                }
                return result;
            } catch (Throwable failure) {
                failures.increment();
                throw failure;
            }
        };
    }

    public JdbcMetricsSnapshot snapshot() {
        return new JdbcMetricsSnapshot(dataSourceCalls.sum(), connectionCalls.sum(), failures.sum());
    }

    public record JdbcMetricsSnapshot(long dataSourceCalls, long connectionCalls, long failures) {
    }
}
