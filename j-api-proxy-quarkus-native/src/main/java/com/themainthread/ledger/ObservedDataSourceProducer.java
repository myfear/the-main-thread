package com.themainthread.ledger;

import java.sql.Connection;

import javax.sql.DataSource;

import io.agroal.api.AgroalDataSource;
import io.github.rrobetti.japiproxy.core.ProxyHandle;
import io.github.rrobetti.japiproxy.jdbc.JdbcProxy;
import io.github.rrobetti.japiproxy.jdbc.JdbcProxyOptions;
import io.quarkus.runtime.annotations.RegisterForProxy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@RegisterForProxy(targets = { DataSource.class, ProxyHandle.class })
@RegisterForProxy(targets = { Connection.class, ProxyHandle.class })
@ApplicationScoped
public class ObservedDataSourceProducer {

    private final AgroalDataSource delegate;
    private final JdbcCallMetrics metrics;

    public ObservedDataSourceProducer(AgroalDataSource delegate, JdbcCallMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Produces
    @ApplicationScoped
    @ObservedDataSource
    DataSource observedDataSource() {
        JdbcProxyOptions options = JdbcProxyOptions.builder()
                .connections(true)
                .statements(false)
                .resultSets(false)
                .build();
        return JdbcProxy.wrap(delegate, "ledger", options, metrics.filter());
    }
}
