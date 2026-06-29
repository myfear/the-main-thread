package io.mainthread.vaultboard.support;

import java.util.List;

import io.mainthread.vaultboard.dashboard.Dashboard;
import io.quarkiverse.multitenancy.core.runtime.context.TenantContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TenantDataCleaner {

    private final TenantContext tenantContext;

    public TenantDataCleaner(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Transactional
    public void clearAll() {
        for (String tenant : List.of("acme", "globex")) {
            tenantContext.setTenantId(tenant);
            Dashboard.deleteAll();
        }
        tenantContext.clear();
    }
}
