package org.mainthread.rcac;

import java.sql.Connection;
import java.sql.SQLException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;

@ApplicationScoped
public class DatabasePools {
    private final AgroalDataSource tenantA;
    private final AgroalDataSource tenantB;
    private final AgroalDataSource tenantAPii;

    @Inject
    public DatabasePools(@DataSource("tenant-a") AgroalDataSource tenantA,
            @DataSource("tenant-b") AgroalDataSource tenantB,
            @DataSource("tenant-a-pii") AgroalDataSource tenantAPii) {
        this.tenantA = tenantA;
        this.tenantB = tenantB;
        this.tenantAPii = tenantAPii;
    }

    public Connection open(PoolIdentity identity) throws SQLException {
        return switch (identity) {
            case TENANT_A -> tenantA.getConnection();
            case TENANT_B -> tenantB.getConnection();
            case TENANT_A_PII -> tenantAPii.getConnection();
        };
    }
}
